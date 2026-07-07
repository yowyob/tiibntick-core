-- liquibase formatted sql
-- changeset manfouo-braun:001-tnt-admin-schema-ensure
-- comment: Ensure the administration schema exists for tnt-administration-core tables
-- (The kernel's administration-core already creates this schema; this is idempotent.)
CREATE SCHEMA IF NOT EXISTS administration;
-- rollback SELECT 1;
