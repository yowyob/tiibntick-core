--liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-billing-invoice — FreelancerOrg Issuer Context ()

--changeset tnt-billing-invoice:002 labels:billing-invoice,freelancer-org
-- comment: Adds FreelancerOrg issuer context and billing template metadata to tnt_invoices.
-- These columns support displaying the FreelancerOrg's tradeName on invoices,
-- tracking template usage, and rendering DSL-generated surcharge details.

ALTER TABLE tnt_invoices
    ADD COLUMN IF NOT EXISTS issuer_org_type       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS issuer_org_id         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS issuer_trade_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS vat_applicable        BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS surcharge_lines_json  JSONB   DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS is_from_template      BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS applied_template_name VARCHAR(200);

COMMENT ON COLUMN tnt_invoices.issuer_org_type
    IS 'Type of org issuing this invoice: AGENCY | FREELANCER_ORG | POINT | LINK. Null for platform invoices.';

COMMENT ON COLUMN tnt_invoices.issuer_org_id
    IS 'UUID of the issuing FreelancerOrg or Agency. References tnt-organization-core — no physical FK.';

COMMENT ON COLUMN tnt_invoices.issuer_trade_name
    IS 'Commercial trade name displayed on the invoice PDF header (e.g. "Moto Express Biyem").';

COMMENT ON COLUMN tnt_invoices.vat_applicable
    IS 'Whether TVA/VAT is applicable for this invoice, based on the issuer registration status.';

COMMENT ON COLUMN tnt_invoices.surcharge_lines_json
    IS 'JSON array of SurchargeLineItem — human-readable billing DSL surcharges for PDF transparency.';

COMMENT ON COLUMN tnt_invoices.is_from_template
    IS 'True if this invoice was generated from a billing policy template (tnt-billing-templates).';

COMMENT ON COLUMN tnt_invoices.applied_template_name
    IS 'Template code/name used to generate the billing policy (e.g. "TPL-FRAGILE").';

-- Index for FreelancerOrg invoice lookup
CREATE INDEX IF NOT EXISTS idx_tnt_invoices_issuer_org
    ON tnt_invoices (issuer_org_id)
    WHERE issuer_org_id IS NOT NULL;

-- Index for template usage analytics
CREATE INDEX IF NOT EXISTS idx_tnt_invoices_template
    ON tnt_invoices (applied_template_name)
    WHERE is_from_template = TRUE;

-- rollback ALTER TABLE tnt_invoices DROP COLUMN IF EXISTS issuer_org_type, DROP COLUMN IF EXISTS issuer_org_id, DROP COLUMN IF EXISTS issuer_trade_name, DROP COLUMN IF EXISTS vat_applicable, DROP COLUMN IF EXISTS surcharge_lines_json, DROP COLUMN IF EXISTS is_from_template, DROP COLUMN IF EXISTS applied_template_name;
