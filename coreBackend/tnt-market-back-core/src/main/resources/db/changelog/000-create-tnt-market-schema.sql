-- liquibase formatted sql
-- changeset manfouo-braun:000-tnt-market-schema
-- comment: Create dedicated schema for tnt-market-back-core (Layer 6 Core Backend — Market product)
CREATE SCHEMA IF NOT EXISTS tnt_market;
-- rollback DROP SCHEMA IF EXISTS tnt_market CASCADE;
