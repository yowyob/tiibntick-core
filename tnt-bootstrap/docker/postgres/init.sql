-- TiiBnTick Core — PostgreSQL Initialization
-- Author: MANFOUO Braun — ENSP Yaoundé 2026
-- This script runs only on first container start (fresh volume).
--
-- LOCAL DEV ONLY. This runs against the disposable docker-compose Postgres
-- container (which grants superuser), never against Yowyob's shared PostgreSQL
-- instance used in staging/prod. On the shared instance:
--   - TiiBnTick does NOT own the server and is not superuser there, but its
--     application role IS granted CREATEDB (scoped so it can only create/own
--     its own database) — LiquibaseConfig.ensureDatabaseExists() creates
--     DB_NAME itself at startup if missing (idempotent; set
--     DB_AUTO_CREATE_DATABASE=false to fall back to manual provisioning).
--     The Yowyob DBA team still provisions, once, out-of-band: the role itself
--     with its CREATEDB grant and a strong secret injected via DB_PASSWORD
--     (see tnt-bootstrap/.env.prod.example).
--   - The DBA team also installs postgis / postgis_topology / uuid-ossp / pg_trgm
--     on that database once (extension creation needs superuser or a "trusted
--     extension" grant — separate from CREATEDB, the app's role may still lack
--     it); the app's own Liquibase changelogs (e.g. tnt-geo-core's `CREATE
--     EXTENSION IF NOT EXISTS postgis`) then no-op safely since the extension
--     already exists.
--   - RT-comops (Kernel) has its own database on the shared instance — do not
--     assume KERNEL_DB_NAME/KERNEL_DB_HOST default to TiiBnTick's own DB in prod;
--     set them explicitly (see TntDataSourceConfig and the "prod" profile notes
--     in application.yml).

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- For text search acceleration

-- Grant all privileges to the application user
GRANT ALL PRIVILEGES ON DATABASE tiibntick_core TO tiibntick;

-- Test database — credentials hardcoded in application.yml's "test" Spring profile
-- (spring.r2dbc.url / spring.liquibase.* under "PROFILE: test"). Without this role
-- and database, TiiBnTickApplicationTest.contextLoads fails Liquibase auth.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'tiibntick_test') THEN
        CREATE ROLE tiibntick_test LOGIN PASSWORD 'tiibntick_test_pass';
    END IF;
END
$$;

SELECT 'CREATE DATABASE tiibntick_test OWNER tiibntick_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tiibntick_test')
\gexec

GRANT ALL PRIVILEGES ON DATABASE tiibntick_test TO tiibntick_test;

\connect tiibntick_test
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;
\connect tiibntick_core

-- Note: Individual module schemas are created by Liquibase migrations
-- at application startup (tnt_media, tnt_delivery, tnt_geo, etc.)
