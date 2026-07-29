-- ============================================================
-- Migration 058 : Adresse du livreur + adresse et photo
--                 du point-relay
-- Author : TiiBnTickTeam
-- Date   : 2026-07-08
-- ============================================================

-- 1. Adresse du livreur
--    FK optionnelle vers la table addresses.
--    Représente la localisation principale du livreur (domicile / base).
ALTER TABLE delivery_persons
    ADD COLUMN IF NOT EXISTS address_id UUID REFERENCES addresses(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_persons_address_id
    ON delivery_persons(address_id);

-- 2. Adresse du point-relay
--    FK optionnelle vers la table addresses.
--    Localisation physique du magasin servant de point-relay.
ALTER TABLE logistics
    ADD COLUMN IF NOT EXISTS address_id UUID REFERENCES addresses(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_logistics_address_id
    ON logistics(address_id);

-- 3. Photo du magasin (point-relay)
--    Chemin relatif vers l'image stockée sur le serveur.
--    Distinct de storefront_photo (déjà présent) pour permettre
--    une photo principale distincte (facade, enseigne, intérieur).
ALTER TABLE logistics
    ADD COLUMN IF NOT EXISTS shop_photo VARCHAR(500);
