-- liquibase formatted sql
-- changeset manfouo-braun:001-tnt-link-schema
-- comment: Create dedicated schema for tnt-link-back-core (Layer 6 Core Backend — Link product)
CREATE SCHEMA IF NOT EXISTS tnt_link;
-- rollback DROP SCHEMA IF EXISTS tnt_link CASCADE;
