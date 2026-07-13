-- liquibase formatted sql
-- changeset francois:gofp-011 labels:tnt-go-freelancer-point-back-core
-- comment: Politique tarifaire personnalisée de chaque livreur freelancer

CREATE TABLE IF NOT EXISTS gofp.delivery_person_pricing (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id
    price_per_kg            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_cbm           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    price_per_km            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    fragile_surcharge       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    perishable_surcharge    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    base_fee                DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    currency                VARCHAR(10)  NOT NULL DEFAULT 'FCFA',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_dp_pricing       PRIMARY KEY (id),
    CONSTRAINT uq_gofp_dp_pricing       UNIQUE (freelancer_actor_id)
);
