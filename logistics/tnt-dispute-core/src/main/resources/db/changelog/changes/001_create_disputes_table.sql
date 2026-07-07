--liquibase formatted sql
--changeset MANFOUO_Braun:001_create_disputes_table
--comment: Create tnt_disputes table — aggregate root for dispute management

CREATE TABLE IF NOT EXISTS tnt_disputes (
    id                              VARCHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id                       VARCHAR(100)    NOT NULL,
    reference                       VARCHAR(30)     NOT NULL UNIQUE,
    cause                           VARCHAR(50)     NOT NULL,
    category                        VARCHAR(50)     NOT NULL,
    priority                        VARCHAR(20)     NOT NULL,
    status                          VARCHAR(50)     NOT NULL,
    claimant_id                     VARCHAR(100)    NOT NULL,
    claimant_type                   VARCHAR(50)     NOT NULL,
    respondent_id                   VARCHAR(100)    NOT NULL,
    respondent_type                 VARCHAR(50)     NOT NULL,
    mission_id                      VARCHAR(36),
    package_id                      VARCHAR(36),
    tracking_code                   VARCHAR(50),
    description                     TEXT            NOT NULL,
    filed_at                        TIMESTAMP       NOT NULL,
    deadline                        TIMESTAMP,
    assigned_mediator_id            VARCHAR(100),
    created_at                      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- Resolution (flattened VO)
    resolution_type                 VARCHAR(50),
    resolution_compensation_required BOOLEAN,
    resolution_mediator_id          VARCHAR(100),
    resolution_summary              TEXT,
    resolution_occurred_at          TIMESTAMP,

    -- Compensation (flattened VO)
    compensation_amount             NUMERIC(15, 2),
    compensation_currency           VARCHAR(10),
    compensation_method             VARCHAR(50),
    compensation_beneficiary_id     VARCHAR(100),
    compensation_payment_reference  VARCHAR(100),
    compensation_approved_at        TIMESTAMP,
    compensation_paid_at            TIMESTAMP,

    -- SLA Policy (flattened VO)
    sla_response_hours              INTEGER         NOT NULL DEFAULT 24,
    sla_investigation_days          INTEGER         NOT NULL DEFAULT 3,
    sla_resolution_days             INTEGER         NOT NULL DEFAULT 7,
    sla_escalation_days             INTEGER         NOT NULL DEFAULT 5,

    -- Optimistic locking
    version                         INTEGER         NOT NULL DEFAULT 0
);

--rollback DROP TABLE IF EXISTS tnt_disputes;
