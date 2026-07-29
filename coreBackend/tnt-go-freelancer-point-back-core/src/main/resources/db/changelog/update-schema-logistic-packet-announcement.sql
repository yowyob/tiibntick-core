-- Update Logistics table
ALTER TABLE logistics
ADD COLUMN IF NOT EXISTS back_photo VARCHAR(255);
ALTER TABLE logistics
ADD COLUMN IF NOT EXISTS front_photo VARCHAR(255);
ALTER TABLE logistics DROP COLUMN IF EXISTS logistic_image;
-- Update Packets table
ALTER TABLE packets
ADD COLUMN IF NOT EXISTS designation VARCHAR(255);
-- Update Announcements table
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS shipper_name VARCHAR(255);
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS shipper_email VARCHAR(255);
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS shipper_phone VARCHAR(255);
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255);
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(255);