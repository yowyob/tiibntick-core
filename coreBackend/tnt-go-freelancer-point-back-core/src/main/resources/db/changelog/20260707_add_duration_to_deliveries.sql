-- Add duration column to deliveries table (maps to Delivery.duration field)
ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS duration INTEGER;
