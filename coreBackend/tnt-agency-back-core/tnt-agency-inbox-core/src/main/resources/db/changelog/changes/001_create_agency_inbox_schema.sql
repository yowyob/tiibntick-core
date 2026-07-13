--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_inbox_schema
CREATE SCHEMA IF NOT EXISTS agency_inbox;

--rollback DROP SCHEMA IF EXISTS agency_inbox CASCADE;
