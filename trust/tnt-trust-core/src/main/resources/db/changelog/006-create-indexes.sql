-- ============================================================
-- Changeset 006: Performance indexes for tnt_trust schema
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:006-create-indexes

-- ── did_documents ──────────────────────────────────────────────────────────
-- Fast lookup by actor — most common DID query pattern
CREATE INDEX IF NOT EXISTS idx_did_actor_tenant
    ON tnt_trust.did_documents (actor_id, tenant_id)
    WHERE revoked = FALSE;

-- ── delivery_proofs ────────────────────────────────────────────────────────
-- Fil d'Ariane by mission — getByMissionId
CREATE INDEX IF NOT EXISTS idx_delivery_proof_mission
    ON tnt_trust.delivery_proofs (mission_id, tenant_id, confirmed_at ASC);

-- Lookup by package for cross-mission tracking
CREATE INDEX IF NOT EXISTS idx_delivery_proof_package
    ON tnt_trust.delivery_proofs (package_id, tenant_id);

-- Unconfirmed proofs (no tx hash yet) for monitoring
CREATE INDEX IF NOT EXISTS idx_delivery_proof_unconfirmed
    ON tnt_trust.delivery_proofs (tenant_id)
    WHERE blockchain_tx_hash IS NULL;

-- ── custody_transfers ──────────────────────────────────────────────────────
-- Chain of custody by tracking code — getByPackageTrackingCode
CREATE INDEX IF NOT EXISTS idx_custody_tracking_code
    ON tnt_trust.custody_transfers (tracking_code, tenant_id, transferred_at ASC);

-- Chain of custody by package ID
CREATE INDEX IF NOT EXISTS idx_custody_package
    ON tnt_trust.custody_transfers (package_id, tenant_id, transferred_at ASC);

-- ── actor_badges ───────────────────────────────────────────────────────────
-- Badge verification by actor + type
CREATE UNIQUE INDEX IF NOT EXISTS uidx_actor_badge_type
    ON tnt_trust.actor_badges (actor_id, badge_type, tenant_id)
    WHERE revoked = FALSE;

-- rollback
-- DROP INDEX IF EXISTS tnt_trust.idx_did_actor_tenant;
-- DROP INDEX IF EXISTS tnt_trust.idx_delivery_proof_mission;
-- DROP INDEX IF EXISTS tnt_trust.idx_delivery_proof_package;
-- DROP INDEX IF EXISTS tnt_trust.idx_delivery_proof_unconfirmed;
-- DROP INDEX IF EXISTS tnt_trust.idx_custody_tracking_code;
-- DROP INDEX IF EXISTS tnt_trust.idx_custody_package;
-- DROP INDEX IF EXISTS tnt_trust.uidx_actor_badge_type;
