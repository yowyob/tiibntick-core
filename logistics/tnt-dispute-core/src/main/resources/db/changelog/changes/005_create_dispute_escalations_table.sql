--liquibase formatted sql
--changeset MANFOUO_Braun:005_create_dispute_escalations_table
--comment: Create tnt_dispute_escalations table — escalation history per dispute

CREATE TABLE IF NOT EXISTS tnt_dispute_escalations (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    dispute_id      VARCHAR(36)     NOT NULL,
    tenant_id       VARCHAR(100)    NOT NULL,
    escalated_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    escalated_by    VARCHAR(100)    NOT NULL,
    reason          TEXT,
    from_status     VARCHAR(50)     NOT NULL,
    to_status       VARCHAR(50)     NOT NULL,
    assigned_to     VARCHAR(100),

    CONSTRAINT fk_escalation_dispute
        FOREIGN KEY (dispute_id) REFERENCES tnt_disputes(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS tnt_dispute_escalations;
