-- =============================================================================
-- Go-Freelancer de-duplication, lot L4 (gdd-l4-401): generic address book.
-- Persisted addresses and the links between an owner (any id) and an address.
-- Additive and re-runnable: nothing existing is touched. Text columns are TEXT: the legacy Go-Freelancer address
-- columns they receive (gdd-l4-410/411) are VARCHAR without a length.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tnt_geography.tnt_addresses (
    id          UUID             NOT NULL,
    tenant_id   UUID,
    street      TEXT,
    landmark    TEXT,
    district    TEXT,
    city        TEXT             NOT NULL,
    region      TEXT,
    country     TEXT             NOT NULL,
    postal_code TEXT,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT tnt_addresses_pk PRIMARY KEY (id),
    CONSTRAINT tnt_addresses_lat_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT tnt_addresses_lng_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX IF NOT EXISTS idx_tnt_addresses_tenant ON tnt_geography.tnt_addresses (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tnt_addresses_city   ON tnt_geography.tnt_addresses (city);

CREATE TABLE IF NOT EXISTS tnt_geography.tnt_address_links (
    id          UUID        NOT NULL,
    owner_id    UUID        NOT NULL,
    address_id  UUID        NOT NULL,
    label       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT tnt_address_links_pk PRIMARY KEY (id),
    CONSTRAINT tnt_address_links_address_fk FOREIGN KEY (address_id)
        REFERENCES tnt_geography.tnt_addresses (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tnt_address_links_owner_address_label
    ON tnt_geography.tnt_address_links (owner_id, address_id, COALESCE(label, ''));
CREATE INDEX IF NOT EXISTS idx_tnt_address_links_owner   ON tnt_geography.tnt_address_links (owner_id);
CREATE INDEX IF NOT EXISTS idx_tnt_address_links_address ON tnt_geography.tnt_address_links (address_id);
