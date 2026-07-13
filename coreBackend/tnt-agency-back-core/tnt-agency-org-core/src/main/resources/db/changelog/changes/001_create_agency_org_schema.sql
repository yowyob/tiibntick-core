--liquibase formatted sql
--changeset jeff-belekotan:001_create_agency_org_schema
--comment: Agency ERP schema in global TiiBnTick database

CREATE SCHEMA IF NOT EXISTS agency_org;

--rollback DROP SCHEMA IF EXISTS agency_org CASCADE;
