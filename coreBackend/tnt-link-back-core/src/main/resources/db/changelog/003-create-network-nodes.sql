-- liquibase formatted sql
-- changeset manfouo-braun:003-create-network-nodes
-- comment: Create tnt_link.network_nodes — Link's extension over tnt-actor-core/tnt-organization-core entities
CREATE TABLE IF NOT EXISTS tnt_link.network_nodes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ref_type VARCHAR(30) NOT NULL,
    ref_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    trust_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    gamification_level INTEGER NOT NULL DEFAULT 1,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_network_nodes_ref UNIQUE (tenant_id, ref_type, ref_id)
);
CREATE INDEX IF NOT EXISTS idx_network_nodes_bbox ON tnt_link.network_nodes (tenant_id, latitude, longitude);
-- rollback DROP TABLE IF EXISTS tnt_link.network_nodes;
