-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:tnt-actor-002 labels:tnt-actor-core
-- comment: Add incident tracking columns to deliverer and freelancer profiles (tnt-incident-core integration)

-- ============================================================
-- TABLE: deliverer_profiles
-- Add incident_history_count and fraud_flagged_by_incident_id
-- for IActorReputationPort implementation
-- ============================================================

ALTER TABLE tnt_actor.deliverer_profiles
    ADD COLUMN IF NOT EXISTS incident_history_count      INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fraud_flagged_by_incident_id UUID;

-- Index for efficient queries by incident count (e.g. find most incident-prone drivers)
CREATE INDEX IF NOT EXISTS idx_deliverer_incident_count
    ON tnt_actor.deliverer_profiles (tenant_id, incident_history_count)
    WHERE incident_history_count > 0;

-- Index for fraud-flagged deliverers (KYC officers dashboard)
CREATE INDEX IF NOT EXISTS idx_deliverer_fraud_flag
    ON tnt_actor.deliverer_profiles (tenant_id, kyc_status)
    WHERE kyc_status = 'FLAGGED';

-- ============================================================
-- TABLE: freelancer_profiles
-- Add incident_history_count for IActorReputationPort
-- ============================================================

ALTER TABLE tnt_actor.freelancer_profiles
    ADD COLUMN IF NOT EXISTS incident_history_count      INTEGER   NOT NULL DEFAULT 0;

-- Index for incident count queries on freelancers
CREATE INDEX IF NOT EXISTS idx_freelancer_incident_count
    ON tnt_actor.freelancer_profiles (tenant_id, incident_history_count)
    WHERE incident_history_count > 0;

-- Index for fraud-flagged freelancers
CREATE INDEX IF NOT EXISTS idx_freelancer_fraud_flag
    ON tnt_actor.freelancer_profiles (tenant_id, kyc_status)
    WHERE kyc_status = 'FLAGGED';
