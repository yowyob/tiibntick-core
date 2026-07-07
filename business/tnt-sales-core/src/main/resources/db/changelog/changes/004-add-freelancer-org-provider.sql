--liquibase formatted sql
--changeset MANFOUO_Braun:004-add-freelancer-org-provider-to-orders
--comment: Adds FreelancerOrg provider context to sales.orders table ().
-- A SalesOrder can now be associated with a FreelancerOrganization as the logistics provider,
-- in addition to the existing Agency model.

ALTER TABLE sales.orders
    ADD COLUMN IF NOT EXISTS provider_org_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS provider_org_id   VARCHAR(255);

COMMENT ON COLUMN sales.orders.provider_org_type IS
    'Type of the logistics service provider: AGENCY | FREELANCER_ORG. '
    'Null for standard agency orders (backward compat).';

COMMENT ON COLUMN sales.orders.provider_org_id IS
    'UUID of the providing organization (FreelancerOrg or Agency). '
    'References tnt-organization-core UUID — no physical FK (cross-module integration key).';

-- Index for FreelancerOrg-scoped order queries
CREATE INDEX IF NOT EXISTS idx_sales_orders_provider_org
    ON sales.orders (provider_org_id, tenant_id)
    WHERE provider_org_id IS NOT NULL;

--rollback ALTER TABLE sales.orders DROP COLUMN IF EXISTS provider_org_type, DROP COLUMN IF EXISTS provider_org_id;
