--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_hr_schema
CREATE SCHEMA IF NOT EXISTS agency_hr;

--rollback DROP SCHEMA IF EXISTS agency_hr CASCADE;
