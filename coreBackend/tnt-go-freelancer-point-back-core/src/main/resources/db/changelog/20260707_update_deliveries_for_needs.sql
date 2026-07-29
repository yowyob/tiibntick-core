-- Update deliveries table to support delivery needs
-- NOTE: announcement_id column does not exist in the original schema,
--       so the DROP NOT NULL is skipped here.
--       The column is added via changeset 061 instead.

ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS delivery_need_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_deliveries_delivery_need_id'
          AND table_name = 'deliveries'
    ) THEN
        ALTER TABLE deliveries
            ADD CONSTRAINT fk_deliveries_delivery_need_id
            FOREIGN KEY (delivery_need_id) REFERENCES delivery_needs(id) ON DELETE CASCADE;
    END IF;
END$$;

-- Change tarif to DOUBLE PRECISION to support accurate pricing
ALTER TABLE deliveries ALTER COLUMN tarif TYPE DOUBLE PRECISION USING tarif::DOUBLE PRECISION;
