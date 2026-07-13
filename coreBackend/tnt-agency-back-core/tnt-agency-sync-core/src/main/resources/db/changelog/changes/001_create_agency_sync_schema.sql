--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_sync_schema
CREATE SCHEMA IF NOT EXISTS agency_sync;

--rollback DROP SCHEMA IF EXISTS agency_sync CASCADE;
