-- Setzt die Passwoerter der internen Supabase-Rollen auf POSTGRES_PASSWORD.
-- Wird einmalig beim Initialisieren einer leeren Datenbank ausgefuehrt.
\set pgpass `echo "$POSTGRES_PASSWORD"`

ALTER USER authenticator WITH PASSWORD :'pgpass';
ALTER USER pgbouncer WITH PASSWORD :'pgpass';
ALTER USER supabase_auth_admin WITH PASSWORD :'pgpass';
ALTER USER supabase_functions_admin WITH PASSWORD :'pgpass';
ALTER USER supabase_storage_admin WITH PASSWORD :'pgpass';
