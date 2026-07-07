-- ============================================================
-- TiiBnTick Core — tnt-billing-templates
-- Migration: 003_create_billing_template_parameters
--
-- Stores the adjustable parameters for each billing policy template.
-- Each row represents one parameter with its default value,
-- min/max bounds, display labels, and type metadata.
--
-- Author  : MANFOUO Braun
-- Module  : tnt-billing-templates (L5 Billing Engine)
-- Version : 0.0.1
-- Date    : 2026-05-26
-- ============================================================

-- liquibase formatted sql

-- changeset manfouo-braun:003-billing-template-parameters
-- comment: Creates billing_template_parameters table

CREATE TABLE IF NOT EXISTS billing_template_parameters (
    -- Primary key
    id                  UUID            NOT NULL,

    -- FK to billing_policy_templates(id)
    template_id         UUID            NOT NULL,

    -- Technical key used in DSL generation (e.g. basePrice, perKmRate)
    parameter_key       VARCHAR(100)    NOT NULL,

    -- French display label (shown in TiiBnTick UI)
    label_fr            VARCHAR(255),

    -- English display label (used in API responses and i18n)
    label_en            VARCHAR(255),

    -- Default value as string (parsed according to parameter_type)
    default_value       TEXT            NOT NULL,

    -- Minimum acceptable value as string (nullable = no lower bound)
    min_value           TEXT,

    -- Maximum acceptable value as string (nullable = no upper bound)
    max_value           TEXT,

    -- Display unit (XAF, %, km, kg, h, x)
    unit                VARCHAR(20),

    -- Parameter type: MONEY | PERCENTAGE | INTEGER | DECIMAL | BOOLEAN | MULTIPLIER
    parameter_type      VARCHAR(30)     NOT NULL,

    -- Contextual help text displayed as tooltip in the UI
    help_text           TEXT,

    CONSTRAINT pk_billing_template_parameters PRIMARY KEY (id),

    CONSTRAINT uq_billing_template_parameters_key
        UNIQUE (template_id, parameter_key),

    CONSTRAINT fk_billing_template_parameters_template
        FOREIGN KEY (template_id)
        REFERENCES billing_policy_templates(id)
        ON DELETE CASCADE
);

-- Index for template parameter lookup
CREATE INDEX IF NOT EXISTS idx_billing_template_parameters_template_id
    ON billing_template_parameters (template_id);

-- rollback DROP TABLE IF EXISTS billing_template_parameters;
