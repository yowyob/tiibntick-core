--liquibase formatted sql

--changeset tnt-inventory:001 labels:inventory,schema
CREATE TABLE IF NOT EXISTS tnt_stock_entries (
    id                  UUID             NOT NULL PRIMARY KEY,
    tenant_id           UUID             NOT NULL,
    product_id          UUID             NOT NULL,
    warehouse_id        UUID             NOT NULL,
    quantity            DOUBLE PRECISION NOT NULL DEFAULT 0,
    reserved_quantity   DOUBLE PRECISION NOT NULL DEFAULT 0,
    unit                VARCHAR(20)      NOT NULL DEFAULT 'UNIT',
    reorder_threshold   DOUBLE PRECISION,
    last_movement_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, product_id, warehouse_id),
    CONSTRAINT chk_qty_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_reserved_le_qty CHECK (reserved_quantity <= quantity)
);

CREATE INDEX IF NOT EXISTS idx_tnt_stock_tenant ON tnt_stock_entries (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tnt_stock_product ON tnt_stock_entries (tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_tnt_stock_warehouse ON tnt_stock_entries (warehouse_id);

--changeset tnt-inventory:002 labels:inventory,schema
CREATE TABLE IF NOT EXISTS tnt_inventory_movements (
    id               UUID             NOT NULL PRIMARY KEY,
    tenant_id        UUID             NOT NULL,
    stock_entry_id   UUID             NOT NULL REFERENCES tnt_stock_entries(id) ON DELETE RESTRICT,
    product_id       UUID             NOT NULL,
    warehouse_id     UUID             NOT NULL,
    type             VARCHAR(40)      NOT NULL,
    quantity         DOUBLE PRECISION NOT NULL,
    reference        VARCHAR(200),
    notes            TEXT,
    performed_by     UUID,
    occurred_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_movement_entry ON tnt_inventory_movements (stock_entry_id);
CREATE INDEX IF NOT EXISTS idx_tnt_movement_product ON tnt_inventory_movements (tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_tnt_movement_date ON tnt_inventory_movements (occurred_at);

--changeset tnt-inventory:003 labels:inventory,schema
CREATE TABLE IF NOT EXISTS tnt_hub_package_entries (
    id                    UUID         NOT NULL PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    hub_id                UUID         NOT NULL,
    package_id            UUID         NOT NULL,
    tracking_code         VARCHAR(100) NOT NULL UNIQUE,
    storage_location      VARCHAR(100),
    deposited_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    picked_up_at          TIMESTAMPTZ,
    deposited_by_actor_id UUID,
    picked_up_by_actor_id UUID,
    recipient_phone       VARCHAR(30),
    notified              BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_tnt_hub_pkg_hub ON tnt_hub_package_entries (hub_id);
CREATE INDEX IF NOT EXISTS idx_tnt_hub_pkg_tracking ON tnt_hub_package_entries (tracking_code);
CREATE INDEX IF NOT EXISTS idx_tnt_hub_pkg_active ON tnt_hub_package_entries (hub_id, picked_up_at)
    WHERE picked_up_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tnt_hub_pkg_package ON tnt_hub_package_entries (package_id);
