-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:tnt-actor-001 labels:tnt-actor-core

CREATE SCHEMA IF NOT EXISTS tnt_actor;

-- ============================================================
-- TABLE: deliverer_profiles
-- Permanent and freelancer-associated deliverers in TiiBnTick
-- ============================================================
CREATE TABLE IF NOT EXISTS tnt_actor.deliverer_profiles
(
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    actor_id            UUID        NOT NULL,
    actor_status        VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    kyc_status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- GPS location (nullable — updated in real time)
    location_lat        DOUBLE PRECISION,
    location_lng        DOUBLE PRECISION,
    location_accuracy   DOUBLE PRECISION,
    location_timestamp  TIMESTAMPTZ,
    location_source     VARCHAR(20),

    -- Rating
    rating_score        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_total        INTEGER          NOT NULL DEFAULT 0,
    rating_updated_at   TIMESTAMPTZ,

    -- Badges (JSON array)
    badges_json         TEXT             NOT NULL DEFAULT '[]',

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,

    -- TiiBnTick Deliverer-specific
    agency_id           UUID        NOT NULL,
    branch_id           UUID        NOT NULL,
    vehicle_id          UUID,
    mission_active_id   UUID,
    capacity_kg         DOUBLE PRECISION NOT NULL,
    contract_id         UUID,
    deliverer_type      VARCHAR(30) NOT NULL DEFAULT 'PERMANENT',

    CONSTRAINT pk_deliverer_profiles PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uix_deliverer_tenant_actor
    ON tnt_actor.deliverer_profiles (tenant_id, actor_id);

CREATE INDEX IF NOT EXISTS idx_deliverer_agency
    ON tnt_actor.deliverer_profiles (tenant_id, agency_id);

CREATE INDEX IF NOT EXISTS idx_deliverer_branch
    ON tnt_actor.deliverer_profiles (tenant_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_deliverer_status
    ON tnt_actor.deliverer_profiles (tenant_id, actor_status, mission_active_id);

CREATE INDEX IF NOT EXISTS idx_deliverer_location
    ON tnt_actor.deliverer_profiles (tenant_id, location_lat, location_lng)
    WHERE location_lat IS NOT NULL;

-- ============================================================
-- TABLE: freelancer_profiles
-- Independent freelancer deliverers in TiiBnTick
-- ============================================================
CREATE TABLE IF NOT EXISTS tnt_actor.freelancer_profiles
(
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID        NOT NULL,
    actor_id                    UUID        NOT NULL,
    actor_status                VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    kyc_status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    location_lat                DOUBLE PRECISION,
    location_lng                DOUBLE PRECISION,
    location_accuracy           DOUBLE PRECISION,
    location_timestamp          TIMESTAMPTZ,
    location_source             VARCHAR(20),

    rating_score                DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_total                INTEGER          NOT NULL DEFAULT 0,
    rating_updated_at           TIMESTAMPTZ,

    badges_json                 TEXT             NOT NULL DEFAULT '[]',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,

    -- Freelancer-specific
    service_zone_ids_json       TEXT NOT NULL DEFAULT '[]',
    availability_slots_json     TEXT NOT NULL DEFAULT '[]',
    pricing_policy_id           UUID,
    associated_agency_ids_json  TEXT NOT NULL DEFAULT '[]',

    CONSTRAINT pk_freelancer_profiles PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uix_freelancer_tenant_actor
    ON tnt_actor.freelancer_profiles (tenant_id, actor_id);

CREATE INDEX IF NOT EXISTS idx_freelancer_status
    ON tnt_actor.freelancer_profiles (tenant_id, actor_status);

-- ============================================================
-- TABLE: relay_operator_profiles
-- Hub relay operators in TiiBnTick
-- ============================================================
CREATE TABLE IF NOT EXISTS tnt_actor.relay_operator_profiles
(
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID        NOT NULL,
    actor_id                    UUID        NOT NULL,
    actor_status                VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    kyc_status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    location_lat                DOUBLE PRECISION,
    location_lng                DOUBLE PRECISION,
    location_accuracy           DOUBLE PRECISION,
    location_timestamp          TIMESTAMPTZ,
    location_source             VARCHAR(20),

    rating_score                DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_total                INTEGER          NOT NULL DEFAULT 0,
    rating_updated_at           TIMESTAMPTZ,

    badges_json                 TEXT NOT NULL DEFAULT '[]',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,

    -- Relay operator-specific
    hub_id                      UUID        NOT NULL,
    opening_hours_json          TEXT        NOT NULL DEFAULT '[]',
    declared_capacity_parcels   INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_relay_operator_profiles PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uix_relay_operator_tenant_actor
    ON tnt_actor.relay_operator_profiles (tenant_id, actor_id);

CREATE UNIQUE INDEX IF NOT EXISTS uix_relay_operator_hub
    ON tnt_actor.relay_operator_profiles (tenant_id, hub_id);

-- ============================================================
-- TABLE: client_profiles
-- Client (shipper / recipient) profiles in TiiBnTick
-- ============================================================
CREATE TABLE IF NOT EXISTS tnt_actor.client_profiles
(
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID        NOT NULL,
    actor_id                    UUID        NOT NULL,
    actor_status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    kyc_status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    location_lat                DOUBLE PRECISION,
    location_lng                DOUBLE PRECISION,

    rating_score                DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_total                INTEGER          NOT NULL DEFAULT 0,
    rating_updated_at           TIMESTAMPTZ,

    badges_json                 TEXT NOT NULL DEFAULT '[]',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,

    -- Client-specific
    favorite_address_ids_json   TEXT NOT NULL DEFAULT '[]',
    loyalty_score               INTEGER NOT NULL DEFAULT 0,
    preferred_payment_method    VARCHAR(50),

    CONSTRAINT pk_client_profiles PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uix_client_tenant_actor
    ON tnt_actor.client_profiles (tenant_id, actor_id);
