-- Migration 0001 — Nutzerprofil-Tabelle fuer den Login-Slice (Issue #2).
-- Nur so viel Schema, wie der Login braucht (CLAUDE.md Abschnitt 1): eine Zeile
-- pro Auth-Nutzer. Charakterdaten (Level, Stats, EP) folgen in Slice #3.
--
-- Idempotent auf leerer DB anwendbar (CI beweist das durch zweifaches Anwenden):
-- alle Objekte werden mit IF NOT EXISTS / OR REPLACE / DROP IF EXISTS erstellt.

-- 1) Tabelle -----------------------------------------------------------------
create table if not exists public.profiles (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);

-- 2) Row Level Security ------------------------------------------------------
-- RLS ist auf JEDER Tabelle Pflicht (CLAUDE.md Abschnitt 8). Standard: Nutzer
-- sehen/aendern ausschliesslich ihre eigene Zeile. Kein Client-INSERT/DELETE —
-- Zeilen entstehen ausschliesslich serverseitig ueber den Trigger unten.
alter table public.profiles enable row level security;
-- Auch fuer den Tabelleneigentuemer erzwingen, damit kein Weg an RLS vorbeifuehrt.
alter table public.profiles force row level security;

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own
    on public.profiles
    for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own
    on public.profiles
    for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Bewusst KEINE insert/delete-Policy: ohne Policy ist beides fuer authenticated
-- verboten. Das Anlegen uebernimmt der security-definer-Trigger.

-- Grants: RLS wirkt nur, wenn die Rolle ueberhaupt Tabellenrechte hat. Insert/
-- Delete werden nicht gewaehrt — doppelte Absicherung zusaetzlich zu den Policies.
grant select, update on public.profiles to authenticated;

-- 3) Automatisches Anlegen des Profils bei Registrierung ---------------------
-- security definer laeuft mit den Rechten des Eigentuemers und umgeht damit die
-- fehlende insert-Policy — der einzige erlaubte Weg, eine Profilzeile zu erzeugen.
create or replace function public.handle_new_user()
    returns trigger
    language plpgsql
    security definer
    set search_path = public
as $$
begin
    insert into public.profiles (user_id)
    values (new.id)
    on conflict (user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row
    execute function public.handle_new_user();
