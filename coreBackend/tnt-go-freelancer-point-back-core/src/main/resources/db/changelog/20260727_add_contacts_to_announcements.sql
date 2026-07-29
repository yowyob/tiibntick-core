-- Add contact fields to announcements table

ALTER TABLE announcements
ADD COLUMN shipper_first_name VARCHAR(255),
ADD COLUMN shipper_last_name VARCHAR(255),
ADD COLUMN shipper_email VARCHAR(255),
ADD COLUMN shipper_phone VARCHAR(50),
ADD COLUMN recipient_first_name VARCHAR(255),
ADD COLUMN recipient_last_name VARCHAR(255),
ADD COLUMN recipient_email VARCHAR(255),
ADD COLUMN recipient_phone VARCHAR(50);
