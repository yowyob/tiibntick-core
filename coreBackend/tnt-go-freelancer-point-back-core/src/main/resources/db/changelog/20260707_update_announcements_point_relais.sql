-- Update announcements table to support Point Relais destination
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS destination_logistics_id UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS logistics_price DOUBLE PRECISION;
ALTER TABLE announcements ADD CONSTRAINT fk_announcements_destination_logistics_id FOREIGN KEY (destination_logistics_id) REFERENCES logistics(id) ON DELETE SET NULL;
