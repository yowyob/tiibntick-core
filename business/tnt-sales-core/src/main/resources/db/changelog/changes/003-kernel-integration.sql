-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:003-kernel-integration
-- comment: Add optional logical reference to the Yowyob Kernel (RT-comops-sales-core)
ALTER TABLE sales.orders
    ADD COLUMN IF NOT EXISTS kernel_sales_order_id UUID;

-- Partial index: only index rows that actually have a Kernel link
CREATE INDEX IF NOT EXISTS idx_orders_kernel_sales_order
    ON sales.orders (kernel_sales_order_id)
    WHERE kernel_sales_order_id IS NOT NULL;

COMMENT ON COLUMN sales.orders.kernel_sales_order_id
    IS 'Optional logical reference to yow_kernel_db RT-comops-sales-core sales order UUID. '
       'NULL for informal / cash-on-delivery transactions with no Kernel ERP counterpart. '
       'No physical foreign key — cross-database logical reference only.';

-- rollback ALTER TABLE sales.orders DROP COLUMN IF EXISTS kernel_sales_order_id;
