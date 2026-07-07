-- liquibase formatted sql
-- Author: MANFOUO Braun
-- Module: tnt-tp-core (L2 Identity)
-- Description: Creates all TiiBnTick third-party tables.
--
-- Kernel integration: third_party_id references RT-comops-tp-core ThirdParty entity.
-- Integration is LOGICAL — no physical FK to the Kernel database.
-- Existence of referenced ThirdParty is validated at application layer via KernelThirdPartyPort.

-- ============================================================
-- Table: tnt_client_profiles
-- TiiBnTick client profiles — extends Kernel ThirdParty by thirdPartyId reference.
-- third_party_id is the Kernel integration key (NOT NULL).
-- ============================================================
-- changeset manfouo-braun:v0001-create-tnt-client-profiles dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_client_profiles
(
    -- TiiBnTick internal primary key
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Multi-tenant isolation
    tenant_id          UUID         NOT NULL,

    -- Kernel integration key: references RT-comops-tp-core ThirdParty entity.
    -- NOT NULL: every profile must map to an active Kernel ThirdParty.
    -- No physical FK to Kernel DB — integration validated via KernelThirdPartyPort.
    third_party_id     UUID         NOT NULL,

    -- TiiBnTick-specific roles stored as JSON array (e.g., '["SENDER","DELIVERER"]')
    tnt_roles          TEXT         NOT NULL DEFAULT '["SENDER"]',

    -- Simplified KYC status (NOT_SUBMITTED | PENDING_REVIEW | APPROVED | REJECTED | EXPIRED)
    kyc_status         VARCHAR(30)  NOT NULL DEFAULT 'NOT_SUBMITTED',

    -- Phone alias for relay-point anonymity (nullable when not masked)
    phone_alias        VARCHAR(50),
    phone_masked       BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Rating aggregation (updated on each ThirdPartyRating submission)
    average_rating     NUMERIC(3,1),
    rating_count       INTEGER      NOT NULL DEFAULT 0,

    -- Delivery statistics
    total_deliveries   INTEGER      NOT NULL DEFAULT 0,

    -- Localization preferences
    preferred_locale   VARCHAR(10)  NOT NULL DEFAULT 'fr',
    preferred_currency VARCHAR(5)   NOT NULL DEFAULT 'XAF',

    -- Loyalty tier (BRONZE | SILVER | GOLD | PLATINUM) — synced from LoyaltyAccount
    loyalty_tier       VARCHAR(20)  NOT NULL DEFAULT 'BRONZE',

    -- Profile status
    active             BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Audit timestamps (UTC)
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Optimistic locking version
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tnt_client_profiles PRIMARY KEY (id),

    -- Uniqueness: one TNT profile per (tenant, Kernel ThirdParty)
    CONSTRAINT uq_tnt_client_profiles_tenant_tp UNIQUE (tenant_id, third_party_id)
);

-- changeset manfouo-braun:v0001-idx-profiles-tenant dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_client_profiles_tenant ON tnt_client_profiles (tenant_id);

-- changeset manfouo-braun:v0001-idx-profiles-tp dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_client_profiles_tp ON tnt_client_profiles (third_party_id);

-- changeset manfouo-braun:v0001-idx-profiles-kyc dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_client_profiles_kyc ON tnt_client_profiles (kyc_status);

-- ============================================================
-- Table: tnt_kyc_records
-- KYC document submissions and review history.
-- third_party_id is the Kernel integration key (NOT NULL).
-- ============================================================
-- changeset manfouo-braun:v0002-create-tnt-kyc-records dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_kyc_records
(
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID        NOT NULL,

    -- Kernel integration key (RT-comops-tp-core ThirdParty)
    third_party_id        UUID        NOT NULL,

    document_type         VARCHAR(50) NOT NULL,
    document_storage_key  TEXT        NOT NULL,
    selfie_storage_key    TEXT,
    document_number       VARCHAR(100),
    document_expiry_date  DATE,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    rejection_reason      TEXT,
    reviewed_by           UUID,
    submitted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_kyc_records PRIMARY KEY (id)
);

-- changeset manfouo-braun:v0002-idx-kyc-tenant-tp dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_kyc_records_tenant_tp ON tnt_kyc_records (tenant_id, third_party_id);

-- changeset manfouo-braun:v0002-idx-kyc-status dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_kyc_records_status ON tnt_kyc_records (status);

-- ============================================================
-- Table: tnt_loyalty_accounts
-- One loyalty account per (tenant, Kernel ThirdParty) — lazy initialized.
-- third_party_id is the Kernel integration key (NOT NULL).
-- ============================================================
-- changeset manfouo-braun:v0003-create-tnt-loyalty-accounts dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_loyalty_accounts
(
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,

    -- Kernel integration key (RT-comops-tp-core ThirdParty)
    third_party_id   UUID        NOT NULL,

    available_points INTEGER     NOT NULL DEFAULT 0,
    lifetime_points  INTEGER     NOT NULL DEFAULT 0,
    redeemed_points  INTEGER     NOT NULL DEFAULT 0,
    expired_points   INTEGER     NOT NULL DEFAULT 0,
    current_tier     VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_loyalty_accounts PRIMARY KEY (id),
    CONSTRAINT uq_tnt_loyalty_accounts_tenant_tp UNIQUE (tenant_id, third_party_id)
);

-- changeset manfouo-braun:v0003-idx-loyalty-tenant-tp dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_loyalty_accounts_tenant_tp
    ON tnt_loyalty_accounts (tenant_id, third_party_id);

-- ============================================================
-- Table: tnt_loyalty_transactions
-- Audit trail of all loyalty points movements.
-- ============================================================
-- changeset manfouo-braun:v0004-create-tnt-loyalty-transactions dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_loyalty_transactions
(
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    loyalty_account_id UUID        NOT NULL,
    points_delta       INTEGER     NOT NULL,
    type               VARCHAR(50) NOT NULL,
    external_ref       TEXT,
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_loyalty_transactions PRIMARY KEY (id),
    CONSTRAINT fk_tnt_loyalty_tx_account
        FOREIGN KEY (loyalty_account_id) REFERENCES tnt_loyalty_accounts (id) ON DELETE CASCADE
);

-- changeset manfouo-braun:v0004-idx-loyalty-tx-account dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_loyalty_tx_account ON tnt_loyalty_transactions (loyalty_account_id);

-- ============================================================
-- Table: tnt_tp_ratings
-- 1–5 star ratings submitted after delivery missions.
-- rated_third_party_id is the Kernel integration key (NOT NULL).
-- ============================================================
-- changeset manfouo-braun:v0005-create-tnt-tp-ratings dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_tp_ratings
(
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL,

    -- Kernel integration key (RT-comops-tp-core ThirdParty)
    rated_third_party_id  UUID         NOT NULL,

    rater_actor_id        UUID         NOT NULL,
    mission_id            VARCHAR(100),
    score                 NUMERIC(2,1) NOT NULL CHECK (score >= 1.0 AND score <= 5.0),
    comment               TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_tp_ratings PRIMARY KEY (id),

    -- Prevent duplicate rating for the same (mission, rater) pair
    CONSTRAINT uq_tnt_tp_rating_mission_rater UNIQUE (mission_id, rater_actor_id)
);

-- changeset manfouo-braun:v0005-idx-ratings-tenant-tp dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_tp_ratings_tenant_tp
    ON tnt_tp_ratings (tenant_id, rated_third_party_id);

-- ============================================================
-- Table: tnt_phone_aliases
-- Phone alias mapping for relay-point anonymity.
-- third_party_id is the Kernel integration key (NOT NULL).
-- ============================================================
-- changeset manfouo-braun:v0006-create-tnt-phone-aliases dbms:postgresql splitStatements:true endDelimiter:;
CREATE TABLE IF NOT EXISTS tnt_phone_aliases
(
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,

    -- Kernel integration key (RT-comops-tp-core ThirdParty)
    third_party_id   UUID        NOT NULL,

    -- Generated alias number (TiiBnTick-reserved Cameroon prefix format)
    alias            VARCHAR(30) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tnt_phone_aliases PRIMARY KEY (id),
    CONSTRAINT uq_tnt_phone_alias_tenant_tp UNIQUE (tenant_id, third_party_id)
);

-- changeset manfouo-braun:v0006-idx-phone-aliases-alias dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_tnt_phone_aliases_alias ON tnt_phone_aliases (alias);
