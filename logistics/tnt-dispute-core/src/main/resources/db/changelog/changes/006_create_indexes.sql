--liquibase formatted sql
--changeset MANFOUO_Braun:006_create_indexes
--comment: Create performance indexes for frequent dispute query patterns

-- Primary lookup indexes
CREATE INDEX IF NOT EXISTS idx_disputes_tenant_status
    ON tnt_disputes(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_disputes_tenant_priority
    ON tnt_disputes(tenant_id, priority);

CREATE INDEX IF NOT EXISTS idx_disputes_claimant
    ON tnt_disputes(claimant_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_disputes_respondent
    ON tnt_disputes(respondent_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_disputes_package
    ON tnt_disputes(package_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_disputes_mission
    ON tnt_disputes(mission_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_disputes_filed_at
    ON tnt_disputes(filed_at DESC);

CREATE INDEX IF NOT EXISTS idx_disputes_deadline
    ON tnt_disputes(deadline, status);

-- Evidence lookup
CREATE INDEX IF NOT EXISTS idx_evidences_dispute
    ON tnt_dispute_evidences(dispute_id);

CREATE INDEX IF NOT EXISTS idx_evidences_type
    ON tnt_dispute_evidences(dispute_id, evidence_type);

-- Events timeline lookup
CREATE INDEX IF NOT EXISTS idx_events_dispute_time
    ON tnt_dispute_events(dispute_id, occurred_at);

-- Dispute comments lookup
CREATE INDEX IF NOT EXISTS idx_comments_dispute
    ON tnt_dispute_comments(dispute_id, posted_at);

-- Escalations lookup
CREATE INDEX IF NOT EXISTS idx_escalations_dispute
    ON tnt_dispute_escalations(dispute_id, escalated_at);

--rollback DROP INDEX IF EXISTS idx_disputes_tenant_status;
--rollback DROP INDEX IF EXISTS idx_disputes_tenant_priority;
--rollback DROP INDEX IF EXISTS idx_disputes_claimant;
--rollback DROP INDEX IF EXISTS idx_disputes_respondent;
--rollback DROP INDEX IF EXISTS idx_disputes_package;
--rollback DROP INDEX IF EXISTS idx_disputes_mission;
--rollback DROP INDEX IF EXISTS idx_disputes_filed_at;
--rollback DROP INDEX IF EXISTS idx_disputes_deadline;
--rollback DROP INDEX IF EXISTS idx_evidences_dispute;
--rollback DROP INDEX IF EXISTS idx_evidences_type;
--rollback DROP INDEX IF EXISTS idx_events_dispute_time;
--rollback DROP INDEX IF EXISTS idx_comments_dispute;
--rollback DROP INDEX IF EXISTS idx_escalations_dispute;
