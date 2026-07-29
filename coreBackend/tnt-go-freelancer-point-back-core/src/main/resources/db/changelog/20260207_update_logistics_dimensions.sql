-- Migration to replace luggage_max_capacity with detailed dimensions
ALTER TABLE logistics ADD COLUMN IF NOT EXISTS length DOUBLE PRECISION;
ALTER TABLE logistics ADD COLUMN IF NOT EXISTS width DOUBLE PRECISION;
ALTER TABLE logistics ADD COLUMN IF NOT EXISTS height DOUBLE PRECISION;
ALTER TABLE logistics ADD COLUMN IF NOT EXISTS unit VARCHAR(10);

-- Drop the old generic field
ALTER TABLE logistics DROP COLUMN IF EXISTS luggage_max_capacity;
