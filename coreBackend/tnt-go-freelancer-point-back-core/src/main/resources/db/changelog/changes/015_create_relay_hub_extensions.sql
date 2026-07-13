-- liquibase formatted sql
-- changeset francois:gofp-015 labels:tnt-go-freelancer-point-back-core
-- comment: Extension Market du point relais physique
-- Contient les propriétés spécifiques au produit Market absentes de tnt-geo-core.relay_hubs

CREATE TABLE IF NOT EXISTS gofp.relay_hub_extensions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    relay_hub_id            UUID        NOT NULL,  -- → tnt-geo-core.relay_hubs.id (clé unique)

    -- Véhicule associé au point relais (si le gérant a un véhicule)
    plate_number            VARCHAR(50),
    color                   VARCHAR(50),
    logistics_type          VARCHAR(50),   -- MOTO, VOITURE, CAMION, etc.
    logistics_class         VARCHAR(50),
    logistic_image          VARCHAR(500),
    tank_capacity           DOUBLE PRECISION,
    luggage_max_capacity    DOUBLE PRECISION,
    total_seat_number       INTEGER,
    rating                  DOUBLE PRECISION DEFAULT 0.0,

    -- Adresse Market étendue (si différente du node géo)
    address_street          VARCHAR(255),
    address_city            VARCHAR(100),
    address_district        VARCHAR(100),
    address_country         VARCHAR(100),
    shop_photo              VARCHAR(500),

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_relay_hub_ext    PRIMARY KEY (id),
    CONSTRAINT uq_gofp_relay_hub_ext    UNIQUE (relay_hub_id)
);
