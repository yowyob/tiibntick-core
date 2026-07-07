--liquibase formatted sql
-- Author: MANFOUO Braun

--changeset tnt-inventory:004 labels:inventory,kernel-integration
-- Adds the optional logical reference to the Yowyob Kernel (RT-comops-inventory-core).
-- NULL is allowed: informal hub-only stock entries have no Kernel counterpart.
-- NO physical FK cross-database — logical reference only.
ALTER TABLE tnt_stock_entries
    ADD COLUMN IF NOT EXISTS kernel_stock_entry_id UUID;

-- Partial index: only index rows that actually have a Kernel link
CREATE INDEX IF NOT EXISTS idx_tnt_stock_kernel_entry
    ON tnt_stock_entries (kernel_stock_entry_id)
    WHERE kernel_stock_entry_id IS NOT NULL;

COMMENT ON COLUMN tnt_stock_entries.kernel_stock_entry_id
    IS 'Optional logical reference to yow_kernel_db RT-comops-inventory-core stock entry UUID. '
       'NULL for informal/hub-only stock not registered in the Kernel ERP. '
       'No physical foreign key — cross-database logical reference only.';

--changeset tnt-inventory:005 labels:inventory,schema
-- Creates the tnt_inventory_alerts table.
-- Previously alerts were stored only in-memory (ConcurrentHashMap) — now persisted.
-- Enables: alert history across restarts, operator acknowledgement, audit trail.
CREATE TABLE IF NOT EXISTS tnt_inventory_alerts (
    id               UUID        NOT NULL PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    product_id       UUID        NOT NULL,
    warehouse_id     UUID        NOT NULL,
    type             VARCHAR(30) NOT NULL,   -- AlertType enum: LOW_STOCK, OUT_OF_STOCK, etc.
    current_quantity DOUBLE PRECISION NOT NULL,
    threshold        DOUBLE PRECISION,       -- NULL when no threshold was configured
    acknowledged     BOOLEAN     NOT NULL DEFAULT FALSE,
    triggered_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at  TIMESTAMPTZ             -- NULL until operator acknowledges
);

CREATE INDEX IF NOT EXISTS idx_tnt_alert_tenant
    ON tnt_inventory_alerts (tenant_id);

-- Optimised index for the primary query: unacknowledged alerts per tenant
CREATE INDEX IF NOT EXISTS idx_tnt_alert_unacknowledged
    ON tnt_inventory_alerts (tenant_id, triggered_at DESC)
    WHERE acknowledged = FALSE;

CREATE INDEX IF NOT EXISTS idx_tnt_alert_product
    ON tnt_inventory_alerts (tenant_id, product_id);

COMMENT ON TABLE tnt_inventory_alerts
    IS 'Persistent inventory reorder and stockout alerts for TiiBnTick logistics. '
       'Replaces the previous in-memory ConcurrentHashMap — alerts now survive restarts.';
