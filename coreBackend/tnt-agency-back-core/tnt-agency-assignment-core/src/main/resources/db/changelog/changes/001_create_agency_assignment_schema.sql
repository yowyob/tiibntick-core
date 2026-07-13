--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_assignment_schema
--comment: Agency mission projection schema (offline sync MISSION aggregate)

CREATE SCHEMA IF NOT EXISTS agency_assignment;

--rollback DROP SCHEMA IF EXISTS agency_assignment CASCADE;
