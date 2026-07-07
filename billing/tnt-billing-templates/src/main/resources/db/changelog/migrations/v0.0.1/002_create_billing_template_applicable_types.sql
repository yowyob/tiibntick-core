-- ============================================================
-- TiiBnTick Core — tnt-billing-templates
-- Migration: 002_create_billing_template_applicable_types
--
-- Junction table linking billing templates to the actor owner types
-- that are allowed to use them. Provides an alternative normalized
-- representation alongside the JSON column in the main template table.
-- Used for DB-level reporting and analytics queries.
--
-- Author  : MANFOUO Braun
-- Module  : tnt-billing-templates (L5 Billing Engine)
-- Version : 0.0.1
-- Date    : 2026-05-26
-- ============================================================

-- liquibase formatted sql

-- changeset manfouo-braun:002-billing-template-applicable-types
-- comment: Creates billing_template_applicable_types junction table

CREATE TABLE IF NOT EXISTS billing_template_applicable_types (
    -- FK to billing_policy_templates(id)
    template_id         UUID            NOT NULL,

    -- Owner type string (enum name)
    -- Values: AGENCY | FREELANCER_ORG | POINT | LINK | ADMIN | MARKET
    owner_type          VARCHAR(50)     NOT NULL,

    CONSTRAINT pk_billing_template_applicable_types
        PRIMARY KEY (template_id, owner_type),

    CONSTRAINT fk_billing_template_applicable_types_template
        FOREIGN KEY (template_id)
        REFERENCES billing_policy_templates(id)
        ON DELETE CASCADE
);

-- Index for reverse lookup: find all templates for a given owner type
CREATE INDEX IF NOT EXISTS idx_billing_template_applicable_owner_type
    ON billing_template_applicable_types (owner_type);

-- rollback DROP TABLE IF EXISTS billing_template_applicable_types;
