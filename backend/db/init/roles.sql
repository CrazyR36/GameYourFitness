-- Setzt die Passwoerter der internen Supabase-Rollen auf POSTGRES_PASSWORD.
-- Wird einmalig beim Initialisieren einer leeren Datenbank ausgefuehrt.
-- Existenz-geprueft: nicht jede Rolle ist in jeder Image-Version vorhanden,
-- und ein Fehler wuerde die restlichen Zuweisungen abbrechen.
\set pgpass `echo "$POSTGRES_PASSWORD"`

SELECT set_config('init.pgpass', :'pgpass', false);

DO $$
DECLARE
    role_name text;
BEGIN
    FOREACH role_name IN ARRAY ARRAY[
        'authenticator',
        'pgbouncer',
        'supabase_auth_admin',
        'supabase_functions_admin',
        'supabase_storage_admin'
    ] LOOP
        IF EXISTS (SELECT FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('ALTER ROLE %I WITH PASSWORD %L', role_name, current_setting('init.pgpass'));
        ELSE
            RAISE NOTICE 'Rolle % existiert nicht — uebersprungen', role_name;
        END IF;
    END LOOP;
END
$$;
