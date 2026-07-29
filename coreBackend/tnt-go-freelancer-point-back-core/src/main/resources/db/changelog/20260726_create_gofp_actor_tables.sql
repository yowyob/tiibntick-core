-- =============================================================================
-- Migration : Create GOFP actor tables
-- These tables enrich the ATANGA backend's identity entities with data needed
-- by the Go-Freelancer core to operate autonomously (no round-trips for basic
-- identity, double addresses, performance metrics, etc.)
--
-- Hierarchy:  gofp_users  ←  gofp_clients
--             gofp_users  ←  gofp_freelancers  ←  gofp_relay_points
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. gofp_users — base identity mirror for any user (client, freelancer, guest)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gofp_users (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- FK to the authoritative users table in ATANGA backend
    core_user_id        UUID        NOT NULL UNIQUE,

    -- Identity
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    phone               VARCHAR(30),
    password_hash       VARCHAR(255),
    profile_photo_url   VARCHAR(500),

    -- Status
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    role                VARCHAR(50),

    -- Default residence address (FK → addresses.id)
    address_id          UUID,

    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT fk_gofp_users_address
        FOREIGN KEY (address_id) REFERENCES addresses(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gofp_users_core_user_id  ON gofp_users (core_user_id);
CREATE INDEX IF NOT EXISTS idx_gofp_users_email         ON gofp_users (email);

-- -----------------------------------------------------------------------------
-- 2. gofp_clients — client-specific enrichment (composed with gofp_users)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gofp_clients (
    id                          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- FK to authoritative clients table in ATANGA backend
    core_client_id              UUID        NOT NULL UNIQUE,

    -- Composition: all identity data lives in gofp_users
    core_user_id                UUID        NOT NULL,

    -- Client-specific attributes
    status                      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    loyalty_status              VARCHAR(30)  NOT NULL DEFAULT 'BRONZE',
    total_orders                INTEGER      NOT NULL DEFAULT 0,
    rating                      DOUBLE PRECISION,

    -- Default delivery address (FK → addresses.id)
    default_delivery_address_id UUID,

    created_at                  TIMESTAMPTZ  DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT fk_gofp_clients_user
        FOREIGN KEY (core_user_id) REFERENCES gofp_users(core_user_id) ON DELETE CASCADE,
    CONSTRAINT fk_gofp_clients_address
        FOREIGN KEY (default_delivery_address_id) REFERENCES addresses(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gofp_clients_core_client_id ON gofp_clients (core_client_id);
CREATE INDEX IF NOT EXISTS idx_gofp_clients_core_user_id   ON gofp_clients (core_user_id);

-- -----------------------------------------------------------------------------
-- 3. gofp_freelancers — freelancer enrichment with double address
--    residence_address_id       : where the freelancer lives (HOME)
--    operational_base_address_id: delivery starting point (WORK/BASE)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gofp_freelancers (
    id                          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- FK to authoritative freelancers / delivery_persons table in ATANGA backend
    core_freelancer_id          UUID        NOT NULL UNIQUE,

    -- Composition: identity lives in gofp_users
    core_user_id                UUID        NOT NULL,

    -- Professional identity
    commercial_name             VARCHAR(255),
    commercial_register         VARCHAR(100),
    taxpayer_number             VARCHAR(100),
    siret                       VARCHAR(50),

    -- Status & activation
    status                      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    is_active                   BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Performance metrics
    rating                      DOUBLE PRECISION,
    total_deliveries            INTEGER      NOT NULL DEFAULT 0,
    failed_deliveries           INTEGER      NOT NULL DEFAULT 0,
    remaining_deliveries        INTEGER,

    -- Financials
    commission_rate             DOUBLE PRECISION,
    subscription_id             UUID,

    -- Real-time GPS
    latitude_gps                FLOAT,
    longitude_gps               FLOAT,

    -- Double address
    -- 1) Residence: HOME address, fallback for proximity matching
    residence_address_id        UUID,
    -- 2) Operational base: WORK/BASE, starting point for assignments (priority)
    operational_base_address_id UUID,

    created_at                  TIMESTAMPTZ  DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT fk_gofp_freelancers_user
        FOREIGN KEY (core_user_id) REFERENCES gofp_users(core_user_id) ON DELETE CASCADE,
    CONSTRAINT fk_gofp_freelancers_residence
        FOREIGN KEY (residence_address_id) REFERENCES addresses(id) ON DELETE SET NULL,
    CONSTRAINT fk_gofp_freelancers_base
        FOREIGN KEY (operational_base_address_id) REFERENCES addresses(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gofp_freelancers_core_id   ON gofp_freelancers (core_freelancer_id);
CREATE INDEX IF NOT EXISTS idx_gofp_freelancers_user      ON gofp_freelancers (core_user_id);
CREATE INDEX IF NOT EXISTS idx_gofp_freelancers_status    ON gofp_freelancers (status);
CREATE INDEX IF NOT EXISTS idx_gofp_freelancers_gps
    ON gofp_freelancers (latitude_gps, longitude_gps)
    WHERE latitude_gps IS NOT NULL AND longitude_gps IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 4. gofp_relay_points — relay point enrichment with double address
--    postal_address_id         : official mailing / billing address (POSTAL)
--    physical_access_address_id: address clients navigate to (PHYSICAL/MAP)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gofp_relay_points (
    id                          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- FK to authoritative logistics table in ATANGA backend
    core_relay_point_id         UUID        NOT NULL UNIQUE,

    -- Owner (FK → gofp_freelancers.core_freelancer_id)
    core_freelancer_id          UUID        NOT NULL,

    -- Owner contact denormalised for fast notification lookup
    -- (avoids join through gofp_freelancers → gofp_users when notifying)
    owner_first_name            VARCHAR(100),
    owner_last_name             VARCHAR(100),
    owner_email                 VARCHAR(255),
    owner_phone                 VARCHAR(30),

    -- Relay point identity
    name                        VARCHAR(255) NOT NULL,
    status                      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    is_active                   BOOLEAN      NOT NULL DEFAULT FALSE,
    rating                      DOUBLE PRECISION,
    total_deposits              INTEGER      NOT NULL DEFAULT 0,

    -- Subscription quota
    subscription_id             UUID,
    deposits_used               INTEGER      NOT NULL DEFAULT 0,
    max_deposits                INTEGER,

    -- Double address
    -- 1) Postal: official billing / administrative address
    postal_address_id           UUID,
    -- 2) Physical access: what's shown on the map, used for routing
    physical_access_address_id  UUID,

    created_at                  TIMESTAMPTZ  DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT fk_gofp_relay_points_freelancer
        FOREIGN KEY (core_freelancer_id) REFERENCES gofp_freelancers(core_freelancer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_gofp_relay_points_postal
        FOREIGN KEY (postal_address_id) REFERENCES addresses(id) ON DELETE SET NULL,
    CONSTRAINT fk_gofp_relay_points_physical
        FOREIGN KEY (physical_access_address_id) REFERENCES addresses(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gofp_relay_points_core_id     ON gofp_relay_points (core_relay_point_id);
CREATE INDEX IF NOT EXISTS idx_gofp_relay_points_freelancer  ON gofp_relay_points (core_freelancer_id);
CREATE INDEX IF NOT EXISTS idx_gofp_relay_points_status      ON gofp_relay_points (status);
CREATE INDEX IF NOT EXISTS idx_gofp_relay_points_active
    ON gofp_relay_points (is_active, status)
    WHERE is_active = TRUE;
