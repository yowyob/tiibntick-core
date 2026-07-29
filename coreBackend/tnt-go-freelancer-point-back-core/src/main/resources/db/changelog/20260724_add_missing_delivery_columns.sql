-- Add missing columns to deliveries table
-- announcement_id: links the delivery to its originating announcement
-- freelancer_id:   the delivery person executing the delivery
-- status:          current lifecycle status of the delivery

ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS announcement_id UUID,
    ADD COLUMN IF NOT EXISTS freelancer_id   UUID,
    ADD COLUMN IF NOT EXISTS status          VARCHAR(50);

-- Optional FK to announcements (non-blocking: no NOT NULL constraint)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_deliveries_announcement'
          AND table_name = 'deliveries'
    ) THEN
        ALTER TABLE deliveries
            ADD CONSTRAINT fk_deliveries_announcement
            FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE SET NULL;
    END IF;
END$$;
