-- Go-Freelancer de-duplication, lot L4 (gdd-l4-403): a hub package entry may carry the person who will collect
-- it and the fees agreed for its storage. Additive, nullable, re-runnable.
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS owner_actor_id UUID;
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS storage_fee    NUMERIC(14,2);
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS penalty_fee    NUMERIC(14,2);
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS fee_currency   VARCHAR(3);
CREATE INDEX IF NOT EXISTS idx_tnt_hub_pkg_owner ON tnt_hub_package_entries (owner_actor_id);
