--liquibase formatted sql
--changeset MANFOUO_Braun:007_add_freelancer_org_to_disputes
--comment: Adds FreelancerOrg respondent context fields to tnt_disputes table ().
-- These columns support tracking when a FreelancerOrganization is the dispute respondent,
-- including sub-deliverer involvement traceability.

ALTER TABLE tnt_disputes
    ADD COLUMN IF NOT EXISTS respondent_org_id          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS implied_sub_deliverer_id   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sub_deliverer_involved     BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN tnt_disputes.respondent_org_id
    IS 'UUID of the respondent FreelancerOrg or Agency. No physical FK — cross-module reference.';

COMMENT ON COLUMN tnt_disputes.implied_sub_deliverer_id
    IS 'UUID of the sub-deliverer implicated in the dispute. Null if OWNER executed directly.';

COMMENT ON COLUMN tnt_disputes.sub_deliverer_involved
    IS 'Whether a SUB_DELIVERER from the FreelancerOrg is involved in this dispute.';

-- Index for FreelancerOrg dispute queries (admin dashboard, org owner dashboard)
CREATE INDEX IF NOT EXISTS idx_tnt_disputes_respondent_org
    ON tnt_disputes (respondent_org_id, tenant_id)
    WHERE respondent_org_id IS NOT NULL;

-- Index for sub-deliverer dispute traceability
CREATE INDEX IF NOT EXISTS idx_tnt_disputes_sub_deliverer
    ON tnt_disputes (implied_sub_deliverer_id)
    WHERE implied_sub_deliverer_id IS NOT NULL;

--rollback ALTER TABLE tnt_disputes DROP COLUMN IF EXISTS respondent_org_id, DROP COLUMN IF EXISTS implied_sub_deliverer_id, DROP COLUMN IF EXISTS sub_deliverer_involved;
