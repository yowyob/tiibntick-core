--liquibase formatted sql
--changeset MANFOUO_Braun:006-add-freelancer-org-accounts
--comment: Adds FreelancerOrg ownership context to accounting.accounts table ().
-- Enables per-FreelancerOrg account segregation with codes 411-FRL-{id}, 421-FRL-{id}, 706-FRL-{id}.

ALTER TABLE accounting.accounts
    ADD COLUMN IF NOT EXISTS owner_org_id   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS owner_org_type VARCHAR(30);

COMMENT ON COLUMN accounting.accounts.owner_org_id IS
    'UUID of the FreelancerOrg owning this account. '
    'Null for standard platform/agency accounts. '
    'References tnt-organization-core UUID — no physical FK (cross-module integration key).';

COMMENT ON COLUMN accounting.accounts.owner_org_type IS
    'FREELANCER_ORG or AGENCY — type of the owning entity. Null for shared platform accounts.';

-- Index for FreelancerOrg account lookup (getFreelancerOrgAccounts endpoint)
CREATE INDEX IF NOT EXISTS idx_accounting_accounts_owner_org
    ON accounting.accounts (owner_org_id, tenant_id)
    WHERE owner_org_id IS NOT NULL;

-- rollback ALTER TABLE accounting.accounts DROP COLUMN IF EXISTS owner_org_id, DROP COLUMN IF EXISTS owner_org_type;
