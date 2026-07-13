-- liquibase formatted sql
-- changeset francois:gofp-012 labels:tnt-go-freelancer-point-back-core
-- comment: Politique tarifaire des points relais (stockage, pénalités)

CREATE TABLE IF NOT EXISTS gofp.logistics_pricing (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    relay_hub_id            UUID        NOT NULL,  -- → tnt-geo-core.relay_hubs.id
    price_per_kg            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_cbm           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_day           DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- frais de stockage journalier
    grace_period_days       INTEGER      NOT NULL DEFAULT 0,        -- jours gratuits avant pénalité
    penalty_per_day         DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- pénalité par jour dépassé
    fragile_surcharge       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    perishable_surcharge    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    base_fee                DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    currency                VARCHAR(10)  NOT NULL DEFAULT 'FCFA',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_log_pricing      PRIMARY KEY (id),
    CONSTRAINT uq_gofp_log_pricing      UNIQUE (relay_hub_id)
);
