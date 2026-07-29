-- Creates the deliveries table.
-- NOTE: delivery_needs table is created at changeset 051, so the FK is added
--       conditionally to avoid a dependency ordering failure.
CREATE TABLE IF NOT EXISTS deliveries (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delivery_need_id UUID,
    tarif            INTEGER,
    note_livreur     REAL,
    pickup_min_time  TIMESTAMP NOT NULL,
    pickup_max_time  TIMESTAMP NOT NULL,
    delivery_min_time TIMESTAMP NOT NULL,
    delivery_max_time TIMESTAMP NOT NULL,
    delivery_note    DOUBLE PRECISION
);

-- Add the FK only if the delivery_needs table already exists at this point
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'delivery_needs'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_delivery_need'
          AND table_name = 'deliveries'
    ) THEN
        ALTER TABLE deliveries
            ADD CONSTRAINT fk_delivery_need
            FOREIGN KEY (delivery_need_id) REFERENCES delivery_needs(id) ON DELETE SET NULL;
    END IF;
END$$;
