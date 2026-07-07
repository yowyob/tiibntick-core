--liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-billing-invoice — Fix JSONB columns to TEXT for R2DBC compatibility

--changeset tnt-billing-invoice:003 labels:billing-invoice,r2dbc-fix
-- comment: R2DBC PostgreSQL driver cannot bind Java String to jsonb columns (SQLSTATE 42804).
-- All other modules (delivery, route, dispute, product) store JSON as TEXT.
-- Convert all jsonb columns to text to align with the rest of the project.

ALTER TABLE tnt_invoices
    ALTER COLUMN lines_json           TYPE TEXT USING lines_json::text,
    ALTER COLUMN tax_lines_json       TYPE TEXT USING tax_lines_json::text,
    ALTER COLUMN discounts_json       TYPE TEXT USING discounts_json::text,
    ALTER COLUMN surcharge_lines_json TYPE TEXT USING surcharge_lines_json::text;

-- rollback ALTER TABLE tnt_invoices ALTER COLUMN lines_json TYPE JSONB USING lines_json::jsonb, ALTER COLUMN tax_lines_json TYPE JSONB USING tax_lines_json::jsonb, ALTER COLUMN discounts_json TYPE JSONB USING discounts_json::jsonb, ALTER COLUMN surcharge_lines_json TYPE JSONB USING surcharge_lines_json::jsonb;
