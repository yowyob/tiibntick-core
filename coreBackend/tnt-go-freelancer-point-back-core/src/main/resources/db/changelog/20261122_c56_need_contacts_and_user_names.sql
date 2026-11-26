-- Lot C-56. Additive and idempotent.
-- The sender and recipient contacts typed in the app are kept on the delivery need (they were
-- dropped: the fields existed only in memory), and a user's own name on the gofp user row.
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS sender_first_name VARCHAR(120);
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS sender_last_name VARCHAR(120);
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS sender_phone VARCHAR(32);
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS recipient_first_name VARCHAR(120);
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS recipient_last_name VARCHAR(120);
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(32);
