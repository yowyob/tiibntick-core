--liquibase formatted sql
--changeset MANFOUO_Braun:002-create-domain-event-envelopes-table
--comment: Create event_bus.domain_event_envelopes — the append-only event store backing DomainEventEnvelopeEntity / EventEnvelopeRepository
--
-- Note: timestamps use TIMESTAMP WITHOUT TIME ZONE (not TIMESTAMPTZ) and the
-- headers-equivalent columns elsewhere in this module use TEXT (not JSONB) —
-- matching DomainEventEnvelopeEntity's java.time.LocalDateTime fields and the
-- rest of the project's convention. tnt-billing-invoice's
-- 003_fix_jsonb_columns_to_text.sql documents why: the R2DBC PostgreSQL
-- driver cannot bind a plain Java String to a jsonb column (SQLSTATE 42804).

CREATE TABLE IF NOT EXISTS event_bus.domain_event_envelopes
(
    id                  VARCHAR(36)  NOT NULL,
    correlation_id      VARCHAR(36)  NOT NULL,
    causation_id        VARCHAR(36),
    event_type          VARCHAR(200) NOT NULL,
    aggregate_id        VARCHAR(100) NOT NULL,
    aggregate_type      VARCHAR(100) NOT NULL,
    tenant_id           VARCHAR(36)  NOT NULL,
    solution_code       VARCHAR(10)  NOT NULL,
    payload             TEXT         NOT NULL,
    schema_version      INTEGER      NOT NULL DEFAULT 1,
    payload_hash        VARCHAR(64)  NOT NULL,
    kafka_topic         VARCHAR(200) NOT NULL,
    kafka_partition_key VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    last_error          TEXT,
    occurred_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMP,
    version             INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT pk_domain_event_envelopes PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_dee_aggregate
    ON event_bus.domain_event_envelopes (aggregate_id, aggregate_type, tenant_id);

CREATE INDEX IF NOT EXISTS idx_dee_event_type
    ON event_bus.domain_event_envelopes (event_type, tenant_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_dee_status
    ON event_bus.domain_event_envelopes (status, tenant_id);

CREATE INDEX IF NOT EXISTS idx_dee_correlation
    ON event_bus.domain_event_envelopes (correlation_id, tenant_id);

--rollback DROP TABLE IF EXISTS event_bus.domain_event_envelopes;
