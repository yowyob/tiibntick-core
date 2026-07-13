-- liquibase formatted sql
-- changeset manfouo-braun:006-extend-network-nodes
-- comment: Add trust/gamification/PoL/DID/beacon fields to tnt_link.network_nodes and create trust_links
ALTER TABLE tnt_link.network_nodes
    ADD COLUMN IF NOT EXISTS community_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS heading DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS declared_zone_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS declared_city VARCHAR(200),
    ADD COLUMN IF NOT EXISTS declared_capacity_parcels INTEGER,
    ADD COLUMN IF NOT EXISTS badges VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS last_zone_id UUID,
    ADD COLUMN IF NOT EXISTS zone_transition_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pol_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pol_peer_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pol_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS did_identifier VARCHAR(100),
    ADD COLUMN IF NOT EXISTS did_issuer VARCHAR(100),
    ADD COLUMN IF NOT EXISTS did_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS beacon_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS beacon_message VARCHAR(500),
    ADD COLUMN IF NOT EXISTS beacon_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS beacon_radius_km DOUBLE PRECISION;

CREATE TABLE IF NOT EXISTS tnt_link.trust_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    from_node_id UUID NOT NULL REFERENCES tnt_link.network_nodes (id),
    to_node_id UUID NOT NULL REFERENCES tnt_link.network_nodes (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_trust_links_pair UNIQUE (tenant_id, from_node_id, to_node_id)
);
CREATE INDEX IF NOT EXISTS idx_trust_links_to_node ON tnt_link.trust_links (tenant_id, to_node_id);
-- rollback ALTER TABLE tnt_link.network_nodes DROP COLUMN IF EXISTS community_score, DROP COLUMN IF EXISTS heading, DROP COLUMN IF EXISTS description, DROP COLUMN IF EXISTS declared_zone_name, DROP COLUMN IF EXISTS declared_city, DROP COLUMN IF EXISTS declared_capacity_parcels, DROP COLUMN IF EXISTS badges, DROP COLUMN IF EXISTS last_zone_id, DROP COLUMN IF EXISTS zone_transition_count, DROP COLUMN IF EXISTS pol_verified, DROP COLUMN IF EXISTS pol_peer_count, DROP COLUMN IF EXISTS pol_verified_at, DROP COLUMN IF EXISTS did_identifier, DROP COLUMN IF EXISTS did_issuer, DROP COLUMN IF EXISTS did_verified_at, DROP COLUMN IF EXISTS beacon_active, DROP COLUMN IF EXISTS beacon_message, DROP COLUMN IF EXISTS beacon_expires_at, DROP COLUMN IF EXISTS beacon_radius_km; DROP TABLE IF EXISTS tnt_link.trust_links;
