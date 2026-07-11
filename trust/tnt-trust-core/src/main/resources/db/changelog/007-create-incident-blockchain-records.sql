-- ============================================================
-- Changeset 007: Create incident_blockchain_records table
-- Author: MANFOUO Braun | Module: tnt-trust
-- Context: IBlockchainAuditPort implementation for tnt-incident-core
--
-- Stores blocks of incident-specific blockchain chains.
-- Each incident may own a dedicated chain (prefix: INC-{uuid})
-- when multiple parcels are affected (affectedParcelIds.size() > 1).
-- Integrity is ensured by SHA-256 chaining (previousHash → currentHash).
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:007-create-incident-blockchain-records

CREATE TABLE IF NOT EXISTS tnt_trust.incident_blockchain_records (

    -- Primary key: auto-generated UUID for each block record
    record_id           UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Chain identifier — format: INC-{uuid}
    -- All blocks belonging to the same incident chain share this value.
    chain_id            VARCHAR(100)    NOT NULL,

    -- Sequential block index within the chain (0 = genesis)
    block_index         BIGINT          NOT NULL,

    -- SHA-256 hash of the previous block.
    -- Value is 'GENESIS' for index 0 (no preceding block).
    previous_hash       VARCHAR(64)     NOT NULL,

    -- SHA-256 hash of this block (computed from blockIndex + previousHash + eventType + payload + nonce).
    current_hash        VARCHAR(64)     NOT NULL,

    -- Event type anchored in this block — matches LogisticTrustEventType enum name.
    -- Examples: INCIDENT_CREATED, EVIDENCE_ATTACHED, PARCEL_HANDOVER_COMPLETED, INCIDENT_CLOSED.
    event_type          VARCHAR(80)     NOT NULL,

    -- JSON payload describing the event (incident context, parcel IDs, actor IDs, etc.).
    payload             TEXT            NOT NULL DEFAULT '{}',

    -- Epoch-millisecond timestamp nonce used in SHA-256 hash computation.
    nonce               BIGINT          NOT NULL,

    -- UTC timestamp when this block was appended to the chain.
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- The incident UUID this block belongs to.
    -- Nullable: allows future non-incident chain types if needed.
    incident_id         UUID,

    CONSTRAINT pk_incident_blockchain_records PRIMARY KEY (record_id),

    -- Uniqueness: each (chainId, blockIndex) pair must be unique.
    -- Prevents duplicate blocks at the same position in a chain.
    CONSTRAINT uq_incident_chain_block UNIQUE (chain_id, block_index),

    -- Ensure current_hash is unique across all blocks (no hash collision allowed).
    CONSTRAINT uq_incident_block_hash UNIQUE (current_hash)
);

COMMENT ON TABLE tnt_trust.incident_blockchain_records IS
    'Local incident blockchain chains managed by tnt-trust. '
    'Each row represents one block. Chains are prefixed INC-{uuid}. '
    'Integrity: block[i].previous_hash = block[i-1].current_hash. '
    'Used by IncidentBlockchainAuditAdapter implementing tnt-incident-core IBlockchainAuditPort.';

COMMENT ON COLUMN tnt_trust.incident_blockchain_records.chain_id IS
    'Chain identifier. Format: INC-{uuid}. All blocks in the same chain share this value.';
COMMENT ON COLUMN tnt_trust.incident_blockchain_records.block_index IS
    'Sequential block position within the chain. Index 0 = genesis block.';
COMMENT ON COLUMN tnt_trust.incident_blockchain_records.previous_hash IS
    'SHA-256 hash of the preceding block. Value is GENESIS for block_index=0.';
COMMENT ON COLUMN tnt_trust.incident_blockchain_records.current_hash IS
    'SHA-256 hash of this block, computed over: blockIndex|previousHash|eventType|payload|timestamp|nonce.';
COMMENT ON COLUMN tnt_trust.incident_blockchain_records.event_type IS
    'LogisticTrustEventType name anchored in this block (e.g., INCIDENT_CREATED, PARCEL_HANDOVER_COMPLETED).';
COMMENT ON COLUMN tnt_trust.incident_blockchain_records.incident_id IS
    'UUID of the incident owning this chain. Nullable for extensibility.';

-- rollback DROP TABLE IF EXISTS tnt_trust.incident_blockchain_records;
