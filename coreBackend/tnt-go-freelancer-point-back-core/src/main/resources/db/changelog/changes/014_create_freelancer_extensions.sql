-- liquibase formatted sql
-- changeset francois:gofp-014 labels:tnt-go-freelancer-point-back-core
-- comment: Extension Market du profil livreur freelancer
-- Contient les propriétés spécifiques au produit Market absentes de tnt-actor-core.freelancer_profiles

CREATE TABLE IF NOT EXISTS gofp.freelancer_extensions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id (clé unique)

    -- Informations légales / administratives (spécifiques au produit Market)
    commercial_register     VARCHAR(100),
    commercial_name         VARCHAR(255),
    taxpayer_number         VARCHAR(100),
    nui                     VARCHAR(100),          -- Numéro Unique d'Identification (Cameroun)
    siret                   VARCHAR(100),

    -- Photos CNI (KYC côté Market)
    cni_front_photo         VARCHAR(500),
    cni_back_photo          VARCHAR(500),
    profile_photo           VARCHAR(500),

    -- Lien vers le plan d'abonnement Market
    subscription_id         UUID,                  -- → gofp.subscriptions.id

    -- Compteurs Market
    remaining_deliveries    INTEGER      NOT NULL DEFAULT 0,
    failed_deliveries       INTEGER      NOT NULL DEFAULT 0,
    total_deliveries        INTEGER      NOT NULL DEFAULT 0,

    -- Statut Market (peut différer du statut Kernel)
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_freelancer_ext   PRIMARY KEY (id),
    CONSTRAINT uq_gofp_freelancer_ext   UNIQUE (freelancer_actor_id),
    CONSTRAINT fk_gofp_fl_ext_sub       FOREIGN KEY (subscription_id) REFERENCES gofp.subscriptions(id)
);
