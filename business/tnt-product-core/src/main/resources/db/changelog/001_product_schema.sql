--liquibase formatted sql

--changeset tnt-product:001 labels:product,schema
CREATE TABLE IF NOT EXISTS tnt_products (
    id                         UUID          NOT NULL PRIMARY KEY,
    tenant_id                  UUID          NOT NULL,
    sku                        VARCHAR(100)  NOT NULL,
    name                       VARCHAR(255)  NOT NULL,
    description                TEXT,
    category_id                UUID,
    type                       VARCHAR(30)   NOT NULL,
    base_price_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    base_price_currency        VARCHAR(10)   NOT NULL DEFAULT 'XAF',
    unit                       VARCHAR(20)   NOT NULL DEFAULT 'UNIT',
    weight_kg                  DOUBLE PRECISION,
    dimensions_length_cm       DOUBLE PRECISION,
    dimensions_width_cm        DOUBLE PRECISION,
    dimensions_height_cm       DOUBLE PRECISION,
    requires_refrigeration     BOOLEAN       NOT NULL DEFAULT FALSE,
    requires_fragile_handling  BOOLEAN       NOT NULL DEFAULT FALSE,
    is_perishable              BOOLEAN       NOT NULL DEFAULT FALSE,
    is_sensitive               BOOLEAN       NOT NULL DEFAULT FALSE,
    packaging_type             VARCHAR(50),
    hazmat_class               VARCHAR(20),
    special_instructions       TEXT,
    status                     VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    tags                       TEXT,
    attributes_json            TEXT          DEFAULT '{}',
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, sku)
);

CREATE INDEX IF NOT EXISTS idx_tnt_products_tenant ON tnt_products (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tnt_products_status ON tnt_products (status);
CREATE INDEX IF NOT EXISTS idx_tnt_products_category ON tnt_products (category_id) WHERE category_id IS NOT NULL;

--changeset tnt-product:002 labels:product,schema
CREATE TABLE IF NOT EXISTS tnt_product_categories (
    id               UUID          NOT NULL PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    parent_category_id UUID,
    description      TEXT,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_product_cat_tenant ON tnt_product_categories (tenant_id);

--changeset tnt-product:003 labels:product,schema
CREATE TABLE IF NOT EXISTS tnt_service_offers (
    id                    UUID          NOT NULL PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    provider_id           UUID          NOT NULL,
    name                  VARCHAR(255)  NOT NULL,
    description           TEXT,
    type                  VARCHAR(40)   NOT NULL,
    max_weight_kg         DOUBLE PRECISION NOT NULL,
    max_distance_km       DOUBLE PRECISION,
    delivery_window_hours INTEGER       NOT NULL DEFAULT 24,
    coverage_zone_id      UUID,
    policy_id             VARCHAR(200),
    published_on_market   BOOLEAN       NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_offers_tenant ON tnt_service_offers (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tnt_offers_provider ON tnt_service_offers (provider_id);
CREATE INDEX IF NOT EXISTS idx_tnt_offers_market ON tnt_service_offers (tenant_id, published_on_market, status)
    WHERE published_on_market = TRUE AND status = 'ACTIVE';
