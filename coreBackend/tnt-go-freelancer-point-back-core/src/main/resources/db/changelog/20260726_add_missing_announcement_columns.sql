-- Add missing columns to the announcements table.
-- These fields were present in the ATANGA backend but absent from the core entity.
-- recipientId and shipperId are intentionally excluded.

ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS client_id             UUID,
    ADD COLUMN IF NOT EXISTS packet_id             UUID,
    ADD COLUMN IF NOT EXISTS pickup_address_id     UUID,
    ADD COLUMN IF NOT EXISTS delivery_address_id   UUID,
    ADD COLUMN IF NOT EXISTS title                 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description           TEXT,
    ADD COLUMN IF NOT EXISTS status                VARCHAR(50),
    ADD COLUMN IF NOT EXISTS amount                DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS created_at            TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at            TIMESTAMPTZ DEFAULT NOW();

-- Optional FK to addresses (non-blocking: SET NULL on delete)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_announcements_pickup_address'
          AND table_name = 'announcements'
    ) THEN
        ALTER TABLE announcements
            ADD CONSTRAINT fk_announcements_pickup_address
            FOREIGN KEY (pickup_address_id) REFERENCES addresses(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_announcements_delivery_address'
          AND table_name = 'announcements'
    ) THEN
        ALTER TABLE announcements
            ADD CONSTRAINT fk_announcements_delivery_address
            FOREIGN KEY (delivery_address_id) REFERENCES addresses(id) ON DELETE SET NULL;
    END IF;
END$$;
