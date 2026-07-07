-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-organization-core (L2 Identity) — 
-- Description: Creates the tnt_freelancer_organization table.
--              A FreelancerOrganization is an independent delivery operator modelled
--              as a micro-organization (sole proprietorship) in TiiBnTick.
--
-- Kernel integration: organization_id references RT-comops-organization-core
-- (logical reference only — no physical FK — nullable for direct registration).

-- changeset manfouo-braun:v0004-create-freelancer-org dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_freelancer_organization
(
    -- TiiBnTick internal primary key
    id                          UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Optional Kernel integration key (nullable — FreelancerOrgs can be created
    -- without a Kernel org reference via the direct registration flow).
    organization_id             UUID,

    -- Multi-tenant isolation key — always prefixed "FRL-{uuid}".
    -- UNIQUE: one FreelancerOrg per tenant.
    tenant_id                   VARCHAR(100)    NOT NULL,

    -- Commercial trade name displayed to clients (e.g. "Moto Express Nlongkak").
    trade_name                  VARCHAR(255)    NOT NULL,

    -- OWNER actor UUID (from tnt-actor-core). NOT NULL.
    owner_actor_id              UUID            NOT NULL,

    -- Registration lifecycle status (FreelancerRegStatus enum name).
    registration_status         VARCHAR(50)     NOT NULL DEFAULT 'REGISTRATION_PENDING',

    -- KYC verification level (KycLevel enum name: NONE, BASIC, FULL).
    kyc_level                   VARCHAR(20)     NOT NULL DEFAULT 'NONE',

    -- FreelancerBillingProfile (denormalized)
    active_policy_id            UUID,
    default_template_code       VARCHAR(100),
    vat_applicable              BOOLEAN         NOT NULL DEFAULT FALSE,
    tax_id                      VARCHAR(100),

    -- Trust
    trust_score                 DECIMAL(4,2)    NOT NULL DEFAULT 0.00,
    blockchain_did              VARCHAR(500),

    -- FreelancerCapabilities (denormalized scalar fields)
    max_weight_kg               DECIMAL(8,2)    NOT NULL DEFAULT 5.0,
    max_distance_km             DECIMAL(8,2)    NOT NULL DEFAULT 10.0,
    works_weekends              BOOLEAN         NOT NULL DEFAULT FALSE,
    works_nights                BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Comma-separated PackageType codes (e.g. 'STANDARD,FRAGILE').
    accepted_package_type_codes TEXT,

    -- Comma-separated FreelancerSpecialization enum names (e.g. 'MEDICAL_DELIVERY,REFRIGERATED').
    specialization_codes        TEXT,

    -- Audit timestamps (UTC)
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Optimistic locking version (managed by Spring Data R2DBC @Version)
    version                     INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT pk_tnt_freelancer_organization PRIMARY KEY (id),

    -- UNIQUE constraint: one tenant per FreelancerOrg
    CONSTRAINT uq_tnt_freelancer_org_tenant_id UNIQUE (tenant_id),

    -- UNIQUE constraint: trade names must be distinct (case-insensitive enforced via index)
    CONSTRAINT uq_tnt_freelancer_org_trade_name UNIQUE (trade_name)
);

-- Index on owner_actor_id for OWNER lookups
-- changeset manfouo-braun:v0004-idx-freelancer-org-owner dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_org_owner_actor_id
    ON tnt_freelancer_organization (owner_actor_id);

-- Index on registration_status for admin review queue queries
-- changeset manfouo-braun:v0004-idx-freelancer-org-status dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_org_registration_status
    ON tnt_freelancer_organization (registration_status);

-- Index on trust_score for reputation-based ranking queries
-- changeset manfouo-braun:v0004-idx-freelancer-org-trust-score dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_freelancer_org_trust_score
    ON tnt_freelancer_organization (trust_score DESC);
