--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_onboarding_schema
CREATE SCHEMA IF NOT EXISTS agency_onboarding;

--rollback DROP SCHEMA IF EXISTS agency_onboarding CASCADE;
