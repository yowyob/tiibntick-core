-- liquibase formatted sql
-- changeset francois:gofp-007 labels:tnt-go-freelancer-point-back-core
-- comment: Paiements avec breakdown commission Market

CREATE TABLE IF NOT EXISTS gofp.payments (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    delivery_id             UUID        NOT NULL,  -- → gofp.deliveries.id
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id
    client_actor_id         UUID        NOT NULL,  -- → tnt-actor-core.client_profiles.actor_id

    -- Montants
    gross_amount            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    commission_amount       DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- retenu par TiiBnTick
    net_amount              DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- reçu par le livreur
    commission_percent      DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- taux appliqué

    -- Plan d'abonnement au moment du paiement (snapshot)
    subscription_type       VARCHAR(20) NOT NULL DEFAULT 'FREE',

    -- Paiement
    payment_method          VARCHAR(50) NOT NULL DEFAULT 'MOBILE_MONEY',
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_reference   VARCHAR(255),
    paid_at                 TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_payments         PRIMARY KEY (id),
    CONSTRAINT fk_gofp_pay_delivery     FOREIGN KEY (delivery_id) REFERENCES gofp.deliveries(id),
    CONSTRAINT uq_gofp_pay_delivery     UNIQUE (delivery_id)   -- 1 paiement par livraison
);

ALTER TABLE gofp.payments
    ADD CONSTRAINT chk_gofp_pay_status
    CHECK (status IN ('PENDING','PAID','FAILED','REFUNDED','DISPUTED'));

ALTER TABLE gofp.payments
    ADD CONSTRAINT chk_gofp_pay_method
    CHECK (payment_method IN ('MOBILE_MONEY','MTN_MOMO','ORANGE_MONEY','STRIPE','CASH'));
