--liquibase formatted sql
--changeset MANFOUO_Braun:002-add-freelancer-org-report-fields
--comment: Adds FreelancerOrg issuer context and billing template fields to invoice report entries ().
-- These fields enable FreelancerOrg-specific revenue reports, surcharge analytics, and template usage reports.

ALTER TABLE tnt_invoice_report_entries
    ADD COLUMN IF NOT EXISTS issuer_org_type        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS issuer_org_id          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS applied_template_name  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS total_surcharge_amount DECIMAL(15,2) DEFAULT 0;

COMMENT ON COLUMN tnt_invoice_report_entries.issuer_org_type IS
    'Type of the invoicing organization: AGENCY | FREELANCER_ORG | POINT | LINK. '
    'Null for platform-issued invoices.';
COMMENT ON COLUMN tnt_invoice_report_entries.issuer_org_id IS
    'UUID of the issuing FreelancerOrg or Agency. '
    'References tnt-organization-core UUID — no physical FK.';
COMMENT ON COLUMN tnt_invoice_report_entries.applied_template_name IS
    'Name/code of the billing policy template used (e.g., TPL-FRAGILE). '
    'Null when invoice was not generated from a template.';
COMMENT ON COLUMN tnt_invoice_report_entries.total_surcharge_amount IS
    'Total surcharge amount applied to this invoice by the billing DSL. '
    'Sum of all SurchargeLineItem amounts (FRAGILE, NIGHT, REFRIGERATED, etc.).';

-- Index for FreelancerOrg-scoped report queries (generateFreelancerOrgReport)
CREATE INDEX IF NOT EXISTS idx_inv_report_issuer_org
    ON tnt_invoice_report_entries (tenant_id, issuer_org_id, invoice_date)
    WHERE issuer_org_id IS NOT NULL;

-- Index for template usage analytics (generateTemplateUsageReport)
CREATE INDEX IF NOT EXISTS idx_inv_report_template
    ON tnt_invoice_report_entries (tenant_id, applied_template_name, invoice_date)
    WHERE applied_template_name IS NOT NULL;

--rollback ALTER TABLE tnt_invoice_report_entries DROP COLUMN IF EXISTS issuer_org_type, DROP COLUMN IF EXISTS issuer_org_id, DROP COLUMN IF EXISTS applied_template_name, DROP COLUMN IF EXISTS total_surcharge_amount;
