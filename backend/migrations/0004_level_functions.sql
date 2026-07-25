-- Migration 0004 — Serverseitige Level-Erkennung fuer das Level-Up (Issue #5).
-- Spiegelt die Kotlin-Level-Kurve (domain/progression/Progression) als SQL-Funktionen
-- und erweitert log_strength_workout, sodass die RPC das Level VOR und NACH der EP-Vergabe
-- zurueckgibt. Der Level-Aufstieg wird damit SERVERSEITIG erkannt (CLAUDE.md 6/8), nicht
-- nur im Client. Der SQL↔Kotlin-Abgleich passiert im RLS-Testskript (backend-stack-Job).
--
-- Idempotent (create or replace); ein zweiter Lauf ist folgenlos.

-- 1) Level-Kurve als SQL-Funktionen (Spiegel von Progression) --------------------------
-- Kumulative EP, um Level L zu erreichen: XP_STEP_PER_LEVEL/2 * (L-1) * L = 50*(L-1)*L
-- (XP_STEP_PER_LEVEL = 100). L=1 -> 0, L=2 -> 100, L=3 -> 300, L=5 -> 1000, L=10 -> 4500.
create or replace function public.xp_to_reach_level(p_level integer)
    returns bigint
    language sql
    immutable
as $$
    select 50::bigint * (p_level - 1) * p_level;
$$;

-- Hoechstes Level, dessen EP-Schwelle p_total_xp bereits erreicht hat. Float-Schaetzung
-- ueber die geschlossene Form, danach exakte Korrektur in Ganzzahl-Arithmetik — damit das
-- Ergebnis an den Schwellen bitgenau der Kotlin-Binaersuche (levelForXp) entspricht.
create or replace function public.level_for_xp(p_total_xp bigint)
    returns integer
    language plpgsql
    immutable
as $$
declare
    v_xp bigint := greatest(p_total_xp, 0);
    v_level integer;
begin
    -- L <= (1 + sqrt(1 + 4*xp/50)) / 2  (aus 50*(L-1)*L <= xp)
    v_level := floor((1 + sqrt(1 + (4.0 * v_xp) / 50.0)) / 2.0)::integer;
    if v_level < 1 then
        v_level := 1;
    end if;
    -- Korrektur gegen Float-Ungenauigkeit in beide Richtungen (exakte Ganzzahl-Grenzen).
    while public.xp_to_reach_level(v_level + 1) <= v_xp loop
        v_level := v_level + 1;
    end loop;
    while v_level > 1 and public.xp_to_reach_level(v_level) > v_xp loop
        v_level := v_level - 1;
    end loop;
    return v_level;
end;
$$;

-- 2) RPC erweitern: Level vor/nach der EP-Vergabe zurueckgeben --------------------------
-- Ersetzt die Fassung aus 0003 (create or replace). Zusaetzlich zu xp_awarded/total_xp
-- liefert die RPC level_before/level_after; der Client zeigt bei level_after > level_before
-- das Level-Up-Popup. Die Zeile wird beim Lesen des Ausgangs-Level gesperrt (for update),
-- damit parallele Trainings konsistent bleiben.
create or replace function public.log_strength_workout(
    p_exercise text,
    p_sets integer,
    p_reps integer,
    p_weight_kg integer
)
    returns jsonb
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_exercise text := btrim(coalesce(p_exercise, ''));
    v_xp bigint;
    v_workout_id uuid;
    v_total_before bigint;
    v_total_after bigint;
    v_level_before integer;
    v_level_after integer;
begin
    if v_user_id is null then
        raise exception 'Keine gueltige Sitzung' using errcode = '42501';
    end if;

    -- Plausibilisierung (Anti-Cheat) — spiegelt den clientseitigen Validator.
    if char_length(v_exercise) < 1 or char_length(v_exercise) > 60 then
        raise exception 'Uebung ungueltig' using errcode = 'P0001';
    end if;
    if p_sets is null or p_sets < 1 or p_sets > 20 then
        raise exception 'Saetze ausserhalb 1..20' using errcode = 'P0001';
    end if;
    if p_reps is null or p_reps < 1 or p_reps > 100 then
        raise exception 'Wiederholungen ausserhalb 1..100' using errcode = 'P0001';
    end if;
    if p_weight_kg is null or p_weight_kg < 0 or p_weight_kg > 500 then
        raise exception 'Gewicht ausserhalb 0..500' using errcode = 'P0001';
    end if;

    -- EP-Formel (Ganzzahl), identisch zu Progression.strengthWorkoutXp.
    v_xp := (p_sets::bigint * p_reps::bigint * (100 + p_weight_kg)) / 100;

    -- Ausgangsstand sperren und Ausgangs-Level bestimmen. Fehlt die Profilzeile
    -- (sollte durch den Signup-Trigger aus 0001 nie passieren), wird abgebrochen,
    -- BEVOR Training/EP-Event geschrieben werden — kein inkonsistenter Zustand,
    -- keine null-total_xp-Antwort.
    select total_xp into v_total_before
    from public.profiles
    where user_id = v_user_id
    for update;
    if not found then
        raise exception 'Kein Profil fuer Nutzer %', v_user_id using errcode = '42501';
    end if;
    v_level_before := public.level_for_xp(v_total_before);

    insert into public.strength_workouts (user_id, exercise, sets, reps, weight_kg, xp_awarded)
    values (v_user_id, v_exercise, p_sets, p_reps, p_weight_kg, v_xp)
    returning id into v_workout_id;

    insert into public.xp_events (user_id, source, xp_amount, ref_id)
    values (v_user_id, 'strength_workout', v_xp, v_workout_id);

    update public.profiles
    set total_xp = total_xp + v_xp,
        strength = strength + 1
    where user_id = v_user_id
    returning total_xp into v_total_after;

    v_level_after := public.level_for_xp(v_total_after);

    return jsonb_build_object(
        'xp_awarded', v_xp,
        'total_xp', v_total_after,
        'level_before', v_level_before,
        'level_after', v_level_after
    );
end;
$$;

revoke all on function public.log_strength_workout(text, integer, integer, integer) from public;
revoke all on function public.log_strength_workout(text, integer, integer, integer) from anon;
grant execute on function public.log_strength_workout(text, integer, integer, integer) to authenticated;
