-- ============================================================
-- TiiBnTick Core — tnt-sync-core schema migration V1
-- Author: MANFOUO Braun
-- ============================================================

-- Sync Session table: tracks each push+pull sync session lifecycle
CREATE TABLE IF NOT EXISTS tnt_sync_session (
    id                   VARCHAR(36) PRIMARY KEY,
    user_id              VARCHAR(255) NOT NULL,
    tenant_id            VARCHAR(255) NOT NULL,
    device_id            VARCHAR(255),
    since_token          TEXT NOT NULL,
    since_sync_at        TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00',
    started_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMP,
    operations_submitted INTEGER NOT NULL DEFAULT 0,
    operations_applied   INTEGER NOT NULL DEFAULT 0,
    conflicts_detected   INTEGER NOT NULL DEFAULT 0,
    conflicts_resolved   INTEGER NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    result_token         TEXT,
    CONSTRAINT chk_sync_session_status CHECK (status IN ('IN_PROGRESS','COMPLETED','PARTIAL','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_sync_session_user_tenant
    ON tnt_sync_session (user_id, tenant_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_session_status
    ON tnt_sync_session (status, completed_at);

-- Offline Operation table: stores queued operations submitted by clients
CREATE TABLE IF NOT EXISTS tnt_offline_operation (
    id               VARCHAR(36) PRIMARY KEY,
    user_id          VARCHAR(255) NOT NULL,
    tenant_id        VARCHAR(255) NOT NULL,
    device_id        VARCHAR(255),
    type             VARCHAR(50) NOT NULL,
    aggregate_type   VARCHAR(100) NOT NULL,
    aggregate_id     VARCHAR(255) NOT NULL,
    payload          TEXT NOT NULL,
    local_timestamp  TIMESTAMP NOT NULL,
    sequence_number  BIGINT NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    retry_count      INTEGER NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMP,
    error            TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_offline_op_type CHECK (type IN (
        'GPS_UPDATE','MISSION_STATUS_UPDATE','PACKAGE_SCAN',
        'HUB_DEPOSIT','DELIVERY_CONFIRMATION','ANOMALY_REPORT','FORM_SUBMISSION'
    )),
    CONSTRAINT chk_offline_op_status CHECK (status IN (
        'QUEUED','APPLYING','APPLIED','CONFLICT','FAILED','DISCARDED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_offline_op_user_tenant_status
    ON tnt_offline_operation (user_id, tenant_id, status, sequence_number);

CREATE INDEX IF NOT EXISTS idx_offline_op_aggregate
    ON tnt_offline_operation (tenant_id, aggregate_type, aggregate_id);

-- Entity Version table: server-side change log for delta computation
-- This is the single source of truth for what has changed since any given timestamp
CREATE TABLE IF NOT EXISTS tnt_entity_version (
    id                   BIGSERIAL,
    tenant_id            VARCHAR(255) NOT NULL,
    aggregate_type       VARCHAR(100) NOT NULL,
    aggregate_id         VARCHAR(255) NOT NULL,
    version              BIGINT NOT NULL,
    operation            VARCHAR(20) NOT NULL,
    payload_json         TEXT,
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by_user_id   VARCHAR(255) DEFAULT 'system',
    CONSTRAINT pk_entity_version PRIMARY KEY (tenant_id, aggregate_type, aggregate_id),
    CONSTRAINT chk_entity_version_op CHECK (operation IN ('CREATED','UPDATED','DELETED','STATUS_CHANGED'))
);

-- Critical: this index drives the delta sync query performance
CREATE INDEX IF NOT EXISTS idx_entity_version_tenant_updated
    ON tnt_entity_version (tenant_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_entity_version_type_updated
    ON tnt_entity_version (tenant_id, aggregate_type, updated_at DESC);
