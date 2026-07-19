-- Das Postgres-Image liefert auth-/storage-Objekte, die dem Superuser gehoeren.
-- GoTrue (supabase_auth_admin) und Storage (supabase_storage_admin) fuehren ihre
-- Migrationen aber selbst aus (CREATE OR REPLACE / ALTER) und muessen dafuer
-- Eigentuemer sein — sonst: "must be owner of function uid" (SQLSTATE 42501).

ALTER SCHEMA auth OWNER TO supabase_auth_admin;
ALTER SCHEMA storage OWNER TO supabase_storage_admin;

DO $$
DECLARE
    obj record;
BEGIN
    FOR obj IN
        SELECT format('ALTER TABLE auth.%I OWNER TO supabase_auth_admin', c.relname) AS ddl
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'auth' AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE obj.ddl;
    END LOOP;

    FOR obj IN
        SELECT format('ALTER SEQUENCE auth.%I OWNER TO supabase_auth_admin', c.relname) AS ddl
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'auth' AND c.relkind = 'S'
    LOOP
        EXECUTE obj.ddl;
    END LOOP;

    FOR obj IN
        SELECT format('ALTER FUNCTION %s OWNER TO supabase_auth_admin', p.oid::regprocedure) AS ddl
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'auth'
    LOOP
        EXECUTE obj.ddl;
    END LOOP;

    FOR obj IN
        SELECT format('ALTER TABLE storage.%I OWNER TO supabase_storage_admin', c.relname) AS ddl
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'storage' AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE obj.ddl;
    END LOOP;

    FOR obj IN
        SELECT format('ALTER SEQUENCE storage.%I OWNER TO supabase_storage_admin', c.relname) AS ddl
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'storage' AND c.relkind = 'S'
    LOOP
        EXECUTE obj.ddl;
    END LOOP;

    FOR obj IN
        SELECT format('ALTER FUNCTION %s OWNER TO supabase_storage_admin', p.oid::regprocedure) AS ddl
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'storage'
    LOOP
        EXECUTE obj.ddl;
    END LOOP;
END
$$;
