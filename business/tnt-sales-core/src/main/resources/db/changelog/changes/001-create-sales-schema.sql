-- liquibase formatted sql
-- changeset manfouo-braun:001-sales-schema
-- comment: Create dedicated sales schema for tnt-sales-core
CREATE SCHEMA IF NOT EXISTS sales;
-- rollback DROP SCHEMA IF EXISTS sales CASCADE;
