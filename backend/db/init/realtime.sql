-- Schema fuer den Realtime-Dienst (verwaltet seine Migrationen selbst).
create schema if not exists _realtime;
alter schema _realtime owner to postgres;
