-- Migration 0003 — Krafttraining erfassen und dafuer EP erhalten (Issue #4).
-- Fuehrt den EP-Audit-Trail ein, den #3 bewusst noch nicht angelegt hat:
--   * strength_workouts  — die erfasste Trainingseinheit + die zugeteilten EP
--   * xp_events          — generischer Audit-Trail jeder EP-Vergabe (source/amount/ref)
-- Die EP-Vergabe (Berechnung, Audit-Eintrag, Erhoehung von total_xp/strength) laeuft
-- AUSSCHLIESSLICH serverseitig in der security-definer-Funktion log_strength_workout
-- (CLAUDE.md Abschnitt 6/8: nicht manipulierbare Berechnung nie nur im Client).
--
-- Idempotent auf leerer DB anwendbar (CI beweist das durch zweifaches Anwenden):
-- create table/policy/constraint jeweils mit IF NOT EXISTS / DROP IF EXISTS, or replace.

-- 1) Tabellen ----------------------------------------------------------------
create table if not exists public.strength_workouts (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid    not null references auth.users (id) on delete cascade,
    exercise   text    not null,
    sets       integer not null,
    reps       integer not null,
    weight_kg  integer not null,
    xp_awarded bigint  not null,
    created_at timestamptz not null default now()
);

create index if not exists strength_workouts_user_created_idx
    on public.strength_workouts (user_id, created_at desc);

create table if not exists public.xp_events (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid   not null references auth.users (id) on delete cascade,
    source     text   not null,
    xp_amount  bigint not null,
    ref_id     uuid,
    created_at timestamptz not null default now()
);

create index if not exists xp_events_user_created_idx
    on public.xp_events (user_id, created_at desc);

-- 2) Integritaet (Defense in Depth — die Funktion validiert zusaetzlich) ------
-- Die Grenzen spiegeln den clientseitigen StrengthWorkoutValidator (Anti-Cheat,
-- erste Version, Issue #4). Sie liegen serverseitig als letzte Verteidigungslinie.
alter table public.strength_workouts drop constraint if exists strength_workouts_sets_range;
alter table public.strength_workouts add constraint strength_workouts_sets_range
    check (sets between 1 and 20);

alter table public.strength_workouts drop constraint if exists strength_workouts_reps_range;
alter table public.strength_workouts add constraint strength_workouts_reps_range
    check (reps between 1 and 100);

alter table public.strength_workouts drop constraint if exists strength_workouts_weight_range;
alter table public.strength_workouts add constraint strength_workouts_weight_range
    check (weight_kg between 0 and 500);

alter table public.strength_workouts drop constraint if exists strength_workouts_exercise_len;
alter table public.strength_workouts add constraint strength_workouts_exercise_len
    check (char_length(btrim(exercise)) between 1 and 60);

alter table public.strength_workouts drop constraint if exists strength_workouts_xp_nonneg;
alter table public.strength_workouts add constraint strength_workouts_xp_nonneg
    check (xp_awarded >= 0);

alter table public.xp_events drop constraint if exists xp_events_amount_nonneg;
alter table public.xp_events add constraint xp_events_amount_nonneg
    check (xp_amount >= 0);

-- 3) Row Level Security ------------------------------------------------------
-- RLS ist auf JEDER Tabelle Pflicht (CLAUDE.md Abschnitt 8). Beide Tabellen sind
-- client-read-only (wie profiles seit #3): Nutzer sehen ausschliesslich die eigenen
-- Zeilen; geschrieben wird NUR ueber die security-definer-RPC. Kein Client-Insert/
-- -Update/-Delete → keine Cheat-Flaeche fuer EP/Trainings.
alter table public.strength_workouts enable row level security;
alter table public.strength_workouts force row level security;
alter table public.xp_events enable row level security;
alter table public.xp_events force row level security;

drop policy if exists strength_workouts_select_own on public.strength_workouts;
create policy strength_workouts_select_own
    on public.strength_workouts
    for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists xp_events_select_own on public.xp_events;
create policy xp_events_select_own
    on public.xp_events
    for select
    to authenticated
    using (auth.uid() = user_id);

-- Bewusst KEINE insert/update/delete-Policy: ohne Policy ist das fuer authenticated
-- verboten. Zusaetzlich die Default-Grants zuruecknehmen (Supabase-Image vergibt
-- anon/authenticated per ALTER DEFAULT PRIVILEGES teils breite Rechte — sonst liefert
-- ein Fremd-INSERT/-DELETE HTTP 204 statt 403; DECISIONS.md 2026-07-21).
revoke all on public.strength_workouts from anon;
revoke all on public.strength_workouts from authenticated;
revoke all on public.xp_events from anon;
revoke all on public.xp_events from authenticated;

grant select on public.strength_workouts to authenticated;
grant select on public.xp_events to authenticated;

-- service_role (bypasst RLS) darf alles — fuer serverseitige Logik/Diagnose.
grant select, insert, update, delete on public.strength_workouts to service_role;
grant select, insert, update, delete on public.xp_events to service_role;

-- 4) Serverseitige EP-Vergabe ------------------------------------------------
-- Einziger erlaubter Schreibweg. security definer laeuft mit Eigentuemerrechten und
-- umgeht damit RLS/fehlende Insert-Policy; auth.uid() liefert weiterhin den JWT-Nutzer
-- des Requests. Alle Schritte laufen in EINER Transaktion (Funktion = atomar):
--   validieren → strength_workouts einfuegen → xp_events einfuegen → profiles erhoehen.
-- Die EP-Formel spiegelt Progression.strengthWorkoutXp (Kotlin) — der E2E-Test gleicht
-- beide Ende-zu-Ende ab. STR-Zuwachs: +1 pro Einheit (erste Version, Issue #4/#16).
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
    v_total_xp bigint;
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

    insert into public.strength_workouts (user_id, exercise, sets, reps, weight_kg, xp_awarded)
    values (v_user_id, v_exercise, p_sets, p_reps, p_weight_kg, v_xp)
    returning id into v_workout_id;

    insert into public.xp_events (user_id, source, xp_amount, ref_id)
    values (v_user_id, 'strength_workout', v_xp, v_workout_id);

    update public.profiles
    set total_xp = total_xp + v_xp,
        strength = strength + 1
    where user_id = v_user_id
    returning total_xp into v_total_xp;

    return jsonb_build_object('xp_awarded', v_xp, 'total_xp', v_total_xp);
end;
$$;

-- Nur authenticated darf die RPC aufrufen (anon nicht).
revoke all on function public.log_strength_workout(text, integer, integer, integer) from public;
revoke all on function public.log_strength_workout(text, integer, integer, integer) from anon;
grant execute on function public.log_strength_workout(text, integer, integer, integer) to authenticated;
