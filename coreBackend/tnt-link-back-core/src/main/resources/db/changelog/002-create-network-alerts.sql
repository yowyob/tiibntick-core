-- liquibase formatted sql
-- changeset manfouo-braun:002-create-network-alerts
-- comment: Create tnt_link.network_alerts for community-reported traffic alerts (tnt-link-back-core)
CREATE TABLE IF NOT EXISTS tnt_link.network_alerts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    alert_type VARCHAR(40) NOT NULL,
    description VARCHAR(1000),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    confirm_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
-- rollback DROP TABLE IF EXISTS tnt_link.network_alerts;
