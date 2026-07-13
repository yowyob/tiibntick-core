-- liquibase formatted sql
-- changeset manfouo-braun:004-create-dao-zones
-- comment: Create tnt_link.dao_zones, dao_proposals, dao_proposal_votes (community governance)
CREATE TABLE IF NOT EXISTS tnt_link.dao_zones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    center_latitude DOUBLE PRECISION NOT NULL,
    center_longitude DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tnt_link.dao_proposals (
    id UUID PRIMARY KEY,
    zone_id UUID NOT NULL REFERENCES tnt_link.dao_zones (id),
    tenant_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    proposer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    votes_for INTEGER NOT NULL DEFAULT 0,
    votes_against INTEGER NOT NULL DEFAULT 0,
    voting_deadline TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_dao_proposals_zone ON tnt_link.dao_proposals (tenant_id, zone_id, status);

CREATE TABLE IF NOT EXISTS tnt_link.dao_proposal_votes (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES tnt_link.dao_proposals (id),
    voter_id UUID NOT NULL,
    in_favor BOOLEAN NOT NULL,
    voted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dao_proposal_votes_member UNIQUE (proposal_id, voter_id)
);
-- rollback DROP TABLE IF EXISTS tnt_link.dao_proposal_votes; DROP TABLE IF EXISTS tnt_link.dao_proposals; DROP TABLE IF EXISTS tnt_link.dao_zones;
