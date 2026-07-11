-- ============================================================
-- Changeset 001: Create tnt_trust schema
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:001-create-tnt-trust-schema
CREATE SCHEMA IF NOT EXISTS tnt_trust;
COMMENT ON SCHEMA tnt_trust IS 'TiiBnTick Trust module — local cache of blockchain-anchored logistic proofs, DID documents, and custody chains.';
-- rollback DROP SCHEMA IF EXISTS tnt_trust CASCADE;
