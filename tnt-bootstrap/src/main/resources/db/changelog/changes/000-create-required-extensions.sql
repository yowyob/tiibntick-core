--liquibase formatted sql

--changeset MANFOUO_Braun:000-create-required-extensions
--comment: Cross-cutting PostgreSQL extensions required by several modules (tnt-geo-core,
-- tnt-route-core, and any module relying on gen_random_uuid()/trigram search). Centralized
-- here, first in the master changelog, so every module can rely on them being present
-- regardless of module include order — instead of each module re-declaring/racing on them.
-- Requires the DB_USER role to have rights to install these extensions (superuser, or a
-- "trusted extension" grant on a PostgreSQL 13+ instance) — see docker/postgres/init.sql
-- and tnt-bootstrap/.env.prod.example for the production provisioning notes.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

--rollback DROP EXTENSION IF EXISTS pg_trgm;
--rollback DROP EXTENSION IF EXISTS "uuid-ossp";
--rollback DROP EXTENSION IF EXISTS postgis_topology;
--rollback DROP EXTENSION IF EXISTS postgis;
