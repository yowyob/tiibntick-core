-- Lot C-54. Additive and idempotent.
-- The courier's own vehicle (type, plate, up to four photos) is kept on the existing
-- gofreelancer_vehicles row; the sender may ask for a vehicle type on a delivery need.
ALTER TABLE gofreelancer_vehicles ADD COLUMN IF NOT EXISTS vehicle_type VARCHAR(20);
ALTER TABLE gofreelancer_vehicles ADD COLUMN IF NOT EXISTS plate_number VARCHAR(40);
ALTER TABLE gofreelancer_vehicles ADD COLUMN IF NOT EXISTS photo_urls TEXT;
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS required_vehicle_type VARCHAR(20);
