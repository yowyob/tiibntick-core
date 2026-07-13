-- liquibase formatted sql
-- changeset francois:gofp-017 labels:tnt-go-freelancer-point-back-core
-- comment: Plans d'abonnement Market des opérateurs de points relais
-- FREE=30% commission (5 colis max), STANDARD=20% (30 colis), PREMIUM=10% (illimité)

CREATE TABLE IF NOT EXISTS gofp.relay_hub_subscriptions (
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),
    relay_hub_id                UUID        NOT NULL,  -- → tnt-geo-core.relay_hubs.id

    subscription_type           VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    start_date                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date                    TIMESTAMPTZ,
    price                       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    payment_method              VARCHAR(50),

    -- Capacité simultanée de colis (NULL = illimité, plan PREMIUM)
    max_packets_simultaneous    INTEGER,               -- NULL = illimité
    packets_used                INTEGER     NOT NULL DEFAULT 0,

    -- Taux de commission TiiBnTick sur les frais de gardiennage (%)
    commission_percent          DOUBLE PRECISION NOT NULL DEFAULT 30.0,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_relay_hub_subs     PRIMARY KEY (id),
    CONSTRAINT uq_gofp_relay_hub_subs     UNIQUE (relay_hub_id)
);

ALTER TABLE gofp.relay_hub_subscriptions
    ADD CONSTRAINT chk_gofp_rhs_type
    CHECK (subscription_type IN ('FREE','STANDARD','PREMIUM'));

ALTER TABLE gofp.relay_hub_subscriptions
    ADD CONSTRAINT chk_gofp_rhs_status
    CHECK (status IN ('ACTIVE','SUSPENDED','EXPIRED','CANCELLED'));

-- Backfill : chaque hub existant obtient un abonnement FREE par défaut
INSERT INTO gofp.relay_hub_subscriptions (
    relay_hub_id,
    subscription_type,
    status,
    price,
    max_packets_simultaneous,
    packets_used,
    commission_percent,
    start_date
)
SELECT
    relay_hub_id,
    'FREE',
    'ACTIVE',
    0.0,
    5,
    0,
    30.0,
    NOW()
FROM gofp.relay_hub_extensions
WHERE relay_hub_id NOT IN (
    SELECT relay_hub_id FROM gofp.relay_hub_subscriptions
);
