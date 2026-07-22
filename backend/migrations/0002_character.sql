-- Migration 0002 — Charakterdaten fuer den Charakterbildschirm (Issue #3).
-- Erweitert public.profiles um die Progression-Spalten (Level wird aus total_xp
-- ABGELEITET und daher NICHT gespeichert — eine Quelle der Wahrheit, CLAUDE.md 6).
--
-- Idempotent auf leerer DB anwendbar (CI beweist das durch zweifaches Anwenden):
-- add column IF NOT EXISTS, drop constraint/policy IF EXISTS vor create, revoke/grant.

-- 1) Progression-Spalten -----------------------------------------------------
-- Startwerte (erste Version, Issue #3): Rang E, je 10 Stats, 0 EP → Level 1.
-- Der Signup-Trigger aus 0001 legt die Zeile an; die Defaults liefern die Startwerte.
alter table public.profiles
    add column if not exists total_xp    bigint  not null default 0,
    add column if not exists rank        text    not null default 'E',
    add column if not exists strength    integer not null default 10,
    add column if not exists vitality    integer not null default 10,
    add column if not exists agility     integer not null default 10,
    add column if not exists perception  integer not null default 10;

-- 2) Integritaet -------------------------------------------------------------
alter table public.profiles drop constraint if exists profiles_rank_valid;
alter table public.profiles add constraint profiles_rank_valid
    check (rank in ('E', 'D', 'C', 'B', 'A', 'S'));

alter table public.profiles drop constraint if exists profiles_total_xp_nonneg;
alter table public.profiles add constraint profiles_total_xp_nonneg
    check (total_xp >= 0);

alter table public.profiles drop constraint if exists profiles_stats_nonneg;
alter table public.profiles add constraint profiles_stats_nonneg
    check (strength >= 0 and vitality >= 0 and agility >= 0 and perception >= 0);

-- 3) Anti-Cheat: Progression ist serverseitig verwaltet ----------------------
-- total_xp/rank/Stats duerfen NIE client-schreibbar sein (CLAUDE.md 6). #2 hatte
-- profiles ein update-Grant + profiles_update_own-Policy gegeben (ungenutzt).
-- Beides zuruecknehmen → profiles ist client-read-only; alle Aenderungen laufen
-- serverseitig (Trigger jetzt; EP-Funktionen ab #4). Der RLS-Negativtest beweist,
-- dass ein Selbst-PATCH auf total_xp mit 403 abgelehnt wird.
drop policy if exists profiles_update_own on public.profiles;
revoke update on public.profiles from authenticated;
-- select bleibt (in 0001 vergeben) — hier idempotent absichern:
grant select on public.profiles to authenticated;

-- service_role (bypasst RLS) darf lesen/schreiben — fuer serverseitige Logik und
-- das Test-Seeding (nur lokaler/CI-Stack). Explizit, damit es nicht an Image-Defaults haengt.
grant select, insert, update on public.profiles to service_role;
