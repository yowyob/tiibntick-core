-- ============================================================
-- Migration 059 : Ajout du type de véhicule requis sur les annonces
-- Author : TiiBnTickTeam
-- Date   : 2026-07-08
-- ============================================================

-- Champ optionnel : NULL = le client accepte tous les types de véhicules.
-- Valeurs possibles : BIKE, MOTORBIKE, SCOOTER, CAR, VAN, TRUCK, STORE
ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS required_vehicle_type VARCHAR(20);
