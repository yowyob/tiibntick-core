-- ============================================================
-- Changeset 009: FreelancerOrg Trust Extensions ()
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:009-freelancer-org-did-fields

-- Add FreelancerOrg subject context to DID documents
ALTER TABLE tnt_trust.did_documents
    ADD COLUMN IF NOT EXISTS subject_type VARCHAR(30) NOT NULL DEFAULT 'ACTOR',
    ADD COLUMN IF NOT EXISTS org_id       VARCHAR(36) NULL;

COMMENT ON COLUMN tnt_trust.did_documents.subject_type IS
    'Type of the DID subject: ACTOR (default) | AGENCY | FREELANCER_ORG.';
COMMENT ON COLUMN tnt_trust.did_documents.org_id IS
    'UUID of the organization owning this DID (for AGENCY and FREELANCER_ORG subjects). '
    'Null for ACTOR-type DIDs. References tnt-organization-core UUID — no physical FK.';

-- Update DID format check to allow org DIDs: did:tiibntick:{tenant}:org:{orgId}
ALTER TABLE tnt_trust.did_documents
    DROP CONSTRAINT IF EXISTS chk_did_format;
ALTER TABLE tnt_trust.did_documents
    ADD CONSTRAINT chk_did_format CHECK (did LIKE 'did:tiibntick:%');

-- Index for FreelancerOrg DID lookup
CREATE INDEX IF NOT EXISTS idx_did_documents_org_id
    ON tnt_trust.did_documents (org_id, tenant_id)
    WHERE org_id IS NOT NULL;

-- rollback ALTER TABLE tnt_trust.did_documents DROP COLUMN IF EXISTS subject_type, DROP COLUMN IF EXISTS org_id;

-- changeset manfouo_braun:009-freelancer-org-delivery-proof-fields

-- Add FreelancerOrg executor context to delivery proofs
ALTER TABLE tnt_trust.delivery_proofs
    ADD COLUMN IF NOT EXISTS executor_org_id   VARCHAR(36) NULL,
    ADD COLUMN IF NOT EXISTS executor_org_type VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS sub_deliverer_id  VARCHAR(36) NULL;

COMMENT ON COLUMN tnt_trust.delivery_proofs.executor_org_id IS
    'UUID of the FreelancerOrg that executed this delivery. '
    'Null for Agency deliveries. References tnt-organization-core UUID — no physical FK.';
COMMENT ON COLUMN tnt_trust.delivery_proofs.executor_org_type IS
    'FREELANCER_ORG or AGENCY — type of the executing organization.';
COMMENT ON COLUMN tnt_trust.delivery_proofs.sub_deliverer_id IS
    'UUID of the SUB_DELIVERER who physically executed the delivery. '
    'Null if the FreelancerOrg OWNER executed directly or if Agency delivery.';

-- Index for FreelancerOrg proof audit trail
CREATE INDEX IF NOT EXISTS idx_delivery_proofs_executor_org
    ON tnt_trust.delivery_proofs (executor_org_id, tenant_id)
    WHERE executor_org_id IS NOT NULL;

-- rollback ALTER TABLE tnt_trust.delivery_proofs DROP COLUMN IF EXISTS executor_org_id, DROP COLUMN IF EXISTS executor_org_type, DROP COLUMN IF EXISTS sub_deliverer_id;
