-- liquibase formatted sql
-- changeset antigravity:create-contacts-and-update-announcements

CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50)
);

ALTER TABLE announcements ADD COLUMN recipient_id UUID;
ALTER TABLE announcements ADD COLUMN shipper_id UUID;

-- Optional: Drop old columns if they are no longer used at all
ALTER TABLE announcements DROP COLUMN IF EXISTS recipient_firstname;
ALTER TABLE announcements DROP COLUMN IF EXISTS recipient_lastname;
ALTER TABLE announcements DROP COLUMN IF EXISTS recipient_email;
ALTER TABLE announcements DROP COLUMN IF EXISTS recipient_phone;
ALTER TABLE announcements DROP COLUMN IF EXISTS shipper_firstname;
ALTER TABLE announcements DROP COLUMN IF EXISTS shipper_lastname;
ALTER TABLE announcements DROP COLUMN IF EXISTS shipper_email;
ALTER TABLE announcements DROP COLUMN IF EXISTS shipper_phone;
