--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_fleet_schema
CREATE SCHEMA IF NOT EXISTS agency_fleet;

--rollback DROP SCHEMA IF EXISTS agency_fleet CASCADE;
