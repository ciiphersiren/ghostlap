#!/bin/bash
# Runs once, the first time the Postgres container starts with an empty data volume.
# Creates one database per service, each owned by its own login. A service can only
# open its own database, so "each service owns its data" is enforced, not just a convention.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE USER players_app WITH PASSWORD '$PLAYERS_DB_PASSWORD';
    CREATE DATABASE players OWNER players_app;
    REVOKE CONNECT ON DATABASE players FROM PUBLIC;

    CREATE USER race_app WITH PASSWORD '$RACE_DB_PASSWORD';
    CREATE DATABASE race OWNER race_app;
    REVOKE CONNECT ON DATABASE race FROM PUBLIC;
EOSQL
