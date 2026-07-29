-- liquibase formatted sql
-- changeset tiibntick:add-pickup-deadline-to-delivery-needs
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS pickup_deadline TIMESTAMP;
COMMENT ON COLUMN delivery_needs.pickup_deadline IS 'Date et heure maximale à laquelle le freelancer doit aller chercher le colis';
