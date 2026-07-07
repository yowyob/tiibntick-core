-- ============================================================
-- Changeset 002: FreelancerOrg provider links for TntClientProfile ()
-- Author: MANFOUO Braun | Module: tnt-tp-core
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo-braun:v0005-add-provider-links dbms:postgresql splitStatements:true endDelimiter:;

-- Add provider_links_json column to tnt_client_profiles
-- Stores a JSON object mapping provider types to provider IDs.
-- Example: {"AGENCY": "AGY-xxx", "FREELANCER_ORG": "FRL-yyy"}
-- Multiple providers allowed: a client can be linked to both an agency and a FreelancerOrg.
ALTER TABLE tnt_client_profiles
    ADD COLUMN IF NOT EXISTS provider_links_json TEXT NOT NULL DEFAULT '{}';

COMMENT ON COLUMN tnt_client_profiles.provider_links_json IS
    'JSON object: Map<providerType, providerId>. '
    'Keys: AGENCY | FREELANCER_ORG | LINK_NETWORK | HUB_POINT. '
    'Values: UUID of the provider entity. No physical FK — cross-module integration keys.';

-- rollback ALTER TABLE tnt_client_profiles DROP COLUMN IF EXISTS provider_links_json;

-- changeset manfouo-braun:v0005-idx-provider-links-freelancer dbms:postgresql splitStatements:true endDelimiter:;
-- GIN index on the JSONB column for efficient FreelancerOrg client lookup
CREATE INDEX IF NOT EXISTS idx_tnt_client_profiles_freelancer_org
    ON tnt_client_profiles USING GIN ((provider_links_json::jsonb));

-- rollback DROP INDEX IF EXISTS idx_tnt_client_profiles_freelancer_org;
