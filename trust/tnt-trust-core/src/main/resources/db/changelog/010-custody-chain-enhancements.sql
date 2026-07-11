-- ============================================================
-- Changeset 010: Chain of Custody enhancements
-- Author: MANFOUO Braun | Module: tnt-trust
-- Adds GPS coordinates, PoC hash, and custody hash chain to
-- custody_transfers table; relaxes NOT NULL on from_actor_id
-- for first-transfer (sender → courier) support.
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:010-custody-chain-enhancements

-- GPS position at time of transfer
ALTER TABLE tnt_trust.custody_transfers
    ADD COLUMN IF NOT EXISTS gps_lat DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS gps_lng DOUBLE PRECISION NULL;

-- Proof-of-Content hash: SHA-256 of (packageId + actors + type + timestamp + GPS)
ALTER TABLE tnt_trust.custody_transfers
    ADD COLUMN IF NOT EXISTS poc_hash VARCHAR(64) NULL;

-- Hash-chain: each row links back to its predecessor
ALTER TABLE tnt_trust.custody_transfers
    ADD COLUMN IF NOT EXISTS previous_custody_hash VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS custody_hash          VARCHAR(64) NULL;

-- Relax NOT NULL constraints: first transfer has no fromActor / GPS may be absent
ALTER TABLE tnt_trust.custody_transfers
    ALTER COLUMN from_actor_id DROP NOT NULL;

COMMENT ON COLUMN tnt_trust.custody_transfers.gps_lat           IS 'WGS-84 latitude at moment of transfer';
COMMENT ON COLUMN tnt_trust.custody_transfers.gps_lng           IS 'WGS-84 longitude at moment of transfer';
COMMENT ON COLUMN tnt_trust.custody_transfers.poc_hash          IS 'SHA-256 Proof-of-Content hash of this transfer event';
COMMENT ON COLUMN tnt_trust.custody_transfers.previous_custody_hash IS 'custody_hash of the preceding transfer in the chain (NULL for genesis)';
COMMENT ON COLUMN tnt_trust.custody_transfers.custody_hash      IS 'SHA-256 hash chaining poc_hash + previous_custody_hash (Proof-of-Integrity)';

-- Index for Proof-of-Integrity chain walk
CREATE INDEX IF NOT EXISTS idx_custody_transfers_pkg_time
    ON tnt_trust.custody_transfers (package_id, transferred_at ASC);

CREATE INDEX IF NOT EXISTS idx_custody_transfers_custody_hash
    ON tnt_trust.custody_transfers (custody_hash)
    WHERE custody_hash IS NOT NULL;

-- rollback ALTER TABLE tnt_trust.custody_transfers DROP COLUMN IF EXISTS gps_lat, DROP COLUMN IF EXISTS gps_lng, DROP COLUMN IF EXISTS poc_hash, DROP COLUMN IF EXISTS previous_custody_hash, DROP COLUMN IF EXISTS custody_hash;
-- rollback ALTER TABLE tnt_trust.custody_transfers ALTER COLUMN from_actor_id SET NOT NULL;
-- rollback DROP INDEX IF EXISTS tnt_trust.idx_custody_transfers_pkg_time;
-- rollback DROP INDEX IF EXISTS tnt_trust.idx_custody_transfers_custody_hash;
