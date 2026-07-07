--liquibase formatted sql
--changeset MANFOUO_Braun:002_create_dispute_evidences_table
--comment: Create tnt_dispute_evidences table — stores evidence records per dispute

CREATE TABLE IF NOT EXISTS tnt_dispute_evidences (
    id                          VARCHAR(36)     NOT NULL PRIMARY KEY,
    dispute_id                  VARCHAR(36)     NOT NULL,
    tenant_id                   VARCHAR(100)    NOT NULL,
    submitted_by                VARCHAR(100)    NOT NULL,
    submitter_type              VARCHAR(50)     NOT NULL,
    evidence_type               VARCHAR(50)     NOT NULL,
    file_key                    VARCHAR(500),
    description                 TEXT,
    submitted_at                TIMESTAMP       NOT NULL DEFAULT NOW(),
    is_verified                 BOOLEAN         NOT NULL DEFAULT FALSE,
    verified_at                 TIMESTAMP,
    verified_by_mediator_id     VARCHAR(100),
    blockchain_ref              VARCHAR(200),

    CONSTRAINT fk_evidence_dispute
        FOREIGN KEY (dispute_id) REFERENCES tnt_disputes(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS tnt_dispute_evidences;
