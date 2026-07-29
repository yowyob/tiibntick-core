-- Add column to store which delivery person is assigned to an announcement
ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS assigned_delivery_person_id UUID;

-- Add foreign key constraint
ALTER TABLE announcements
    ADD CONSTRAINT fk_announcements_assigned_dp
    FOREIGN KEY (assigned_delivery_person_id)
    REFERENCES delivery_persons(id);
