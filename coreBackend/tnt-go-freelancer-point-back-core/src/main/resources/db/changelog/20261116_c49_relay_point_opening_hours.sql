-- Opening hours of a relay point. 045-add-point-relais-support.sql described this table with a
-- logistics_id column, but no master changelog includes it, and the OpeningHours entity reads
-- relay_point_id. This creates the table the entity reads, or completes an existing one.
-- relay_point_id : hub id of the gofp relay point (same key as gofreelancer_relay_point_visuals).
-- Additive and idempotent: an existing logistics_id column is kept, only made optional.
CREATE TABLE IF NOT EXISTS opening_hours (
    id             UUID PRIMARY KEY,
    relay_point_id UUID,
    day_of_week    VARCHAR(20) NOT NULL,
    open_time      TIME,
    close_time     TIME,
    is_closed      BOOLEAN DEFAULT FALSE
);

ALTER TABLE opening_hours ADD COLUMN IF NOT EXISTS relay_point_id UUID;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'opening_hours' AND column_name = 'logistics_id'
                 AND table_schema = current_schema()) THEN
        ALTER TABLE opening_hours ALTER COLUMN logistics_id DROP NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_opening_hours_relay_point_id ON opening_hours (relay_point_id);
