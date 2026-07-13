--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_commercial_schema
CREATE SCHEMA IF NOT EXISTS agency_commercial;

--rollback DROP SCHEMA IF EXISTS agency_commercial CASCADE;
