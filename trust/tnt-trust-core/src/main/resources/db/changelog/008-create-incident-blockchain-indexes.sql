-- ============================================================
-- Changeset 008: Performance indexes for incident_blockchain_records
-- Author: MANFOUO Braun | Module: tnt-trust
-- Context: IBlockchainAuditPort implementation for tnt-incident-core
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:008-create-incident-blockchain-indexes

-- ── Primary query pattern: find all blocks of a chain in order ────────────
-- Used by IncidentBlockchainAuditAdapter.findAllByChainIdAsc() for chain verification
-- and by writeIncidentEvent() to retrieve the latest block before appending.
CREATE INDEX IF NOT EXISTS idx_incident_chain_index_asc
    ON tnt_trust.incident_blockchain_records (chain_id, block_index ASC);

-- ── Latest block lookup: find the block with highest index in a chain ─────
-- Used by IncidentBlockchainAuditAdapter.findLatestByChainId() (DESC order).
CREATE INDEX IF NOT EXISTS idx_incident_chain_index_desc
    ON tnt_trust.incident_blockchain_records (chain_id, block_index DESC);

-- ── Incident-scoped query: all chains belonging to a given incident ────────
-- Used for incident audit trail aggregation (non-critical path).
CREATE INDEX IF NOT EXISTS idx_incident_blockchain_incident_id
    ON tnt_trust.incident_blockchain_records (incident_id)
    WHERE incident_id IS NOT NULL;

-- ── Event type filtering: audit queries by event type within a chain ──────
CREATE INDEX IF NOT EXISTS idx_incident_blockchain_event_type
    ON tnt_trust.incident_blockchain_records (chain_id, event_type);

-- ── Chronological ordering: most recent blocks across all chains ──────────
-- Used by monitoring and admin dashboards.
CREATE INDEX IF NOT EXISTS idx_incident_blockchain_created_at
    ON tnt_trust.incident_blockchain_records (created_at DESC);

-- rollback
-- DROP INDEX IF EXISTS tnt_trust.idx_incident_chain_index_asc;
-- DROP INDEX IF EXISTS tnt_trust.idx_incident_chain_index_desc;
-- DROP INDEX IF EXISTS tnt_trust.idx_incident_blockchain_incident_id;
-- DROP INDEX IF EXISTS tnt_trust.idx_incident_blockchain_event_type;
-- DROP INDEX IF EXISTS tnt_trust.idx_incident_blockchain_created_at;
