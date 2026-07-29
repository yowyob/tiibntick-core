-- liquibase formatted sql
-- changeset TiiBnTickTeam:20260208-fix-delivery-person-and-logistics-schema
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='delivery_persons' AND column_name='nui') THEN
        ALTER TABLE delivery_persons RENAME COLUMN nui TO taxpayer_number;
    END IF;
END $$;

ALTER TABLE delivery_persons ADD COLUMN IF NOT EXISTS longitude_gps FLOAT;
ALTER TABLE delivery_persons ADD COLUMN IF NOT EXISTS latitude_gps FLOAT;
ALTER TABLE delivery_persons ADD COLUMN IF NOT EXISTS remaining_deliveries INTEGER;
ALTER TABLE delivery_persons ADD COLUMN IF NOT EXISTS failed_deliveries INTEGER;
ALTER TABLE delivery_persons ADD COLUMN IF NOT EXISTS subscription_id UUID;

ALTER TABLE logistics ADD COLUMN IF NOT EXISTS color VARCHAR(50);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='logistics' AND column_name='logistics_photo') THEN
        ALTER TABLE logistics ALTER COLUMN logistics_photo DROP NOT NULL;
    END IF;
END $$;
