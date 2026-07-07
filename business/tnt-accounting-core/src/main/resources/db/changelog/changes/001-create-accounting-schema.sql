-- liquibase formatted sql
-- changeset manfouo-braun:001-accounting-schema
-- comment: Create dedicated accounting schema for tnt-accounting-core
CREATE SCHEMA IF NOT EXISTS accounting;
-- rollback DROP SCHEMA IF EXISTS accounting CASCADE;
