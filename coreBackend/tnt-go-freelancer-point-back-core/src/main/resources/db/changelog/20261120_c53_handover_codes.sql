-- Lot C-53. Additive and idempotent.
-- The two handover codes of a delivery (pickup, delivery) are kept encrypted (AES-256-GCM) so the
-- owner of the delivery need can read them again in the app. The BCrypt hashes stay the reference
-- for verification; these columns are only for reading back.
ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS pickup_code_enc TEXT;
ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS delivery_code_enc TEXT;
