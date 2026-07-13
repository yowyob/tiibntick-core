--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_intake_schema
CREATE SCHEMA IF NOT EXISTS agency_intake;

--rollback DROP SCHEMA IF EXISTS agency_intake CASCADE;
