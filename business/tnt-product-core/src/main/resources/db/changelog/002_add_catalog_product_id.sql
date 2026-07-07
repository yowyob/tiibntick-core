--liquibase formatted sql

--changeset manfouo-braun:002-product-catalog-kernel-integration labels:product,kernel-integration
--comment: Add catalog_product_id integration key to tnt_products and tnt_service_offers.
--         This is a logical reference to yow_kernel_db.products.id (RT-comops-product-core).
--         No physical FK cross-database constraint.

-- Add catalog_product_id to tnt_products
ALTER TABLE tnt_products
    ADD COLUMN IF NOT EXISTS catalog_product_id UUID NULL;

-- Index for efficient lookup by Kernel product UUID
CREATE INDEX IF NOT EXISTS idx_tnt_products_catalog_product_id
    ON tnt_products (catalog_product_id)
    WHERE catalog_product_id IS NOT NULL;

COMMENT ON COLUMN tnt_products.catalog_product_id IS
    'Logical reference to yow_kernel_db.products.id (RT-comops-product-core). '
    'Null for TNT-exclusive products with no Kernel catalog counterpart. '
    'No physical FK constraint — cross-database reference only.';

-- Add catalog_product_id to tnt_service_offers
ALTER TABLE tnt_service_offers
    ADD COLUMN IF NOT EXISTS catalog_product_id UUID NULL;

-- Index for efficient lookup by Kernel product UUID
CREATE INDEX IF NOT EXISTS idx_tnt_offers_catalog_product_id
    ON tnt_service_offers (catalog_product_id)
    WHERE catalog_product_id IS NOT NULL;

COMMENT ON COLUMN tnt_service_offers.catalog_product_id IS
    'Logical reference to yow_kernel_db.products.id (RT-comops-product-core). '
    'Null for general-purpose offers not tied to a specific Kernel product type. '
    'No physical FK constraint — cross-database reference only.';

--rollback ALTER TABLE tnt_products DROP COLUMN IF EXISTS catalog_product_id;
--rollback ALTER TABLE tnt_service_offers DROP COLUMN IF EXISTS catalog_product_id;
