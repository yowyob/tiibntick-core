-- liquibase formatted sql
-- changeset francois:gofp-006 labels:tnt-go-freelancer-point-back-core
-- comment: Plans d'abonnement Market des livreurs freelancers
-- FREE=30% commission, STANDARD=20%, ADVANCE=10% (quota mensuel illimité)

CREATE TABLE IF NOT EXISTS gofp.subscriptions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id
    subscription_type       VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date                TIMESTAMPTZ,
    price                   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    payment_method          VARCHAR(50),

    -- Quota mensuel (ADVANCE = illimité → NULL)
    monthly_quota           INTEGER,               -- NULL = illimité
    deliveries_used         INTEGER     NOT NULL DEFAULT 0,
    reset_date              TIMESTAMPTZ,           -- date de réinitialisation mensuelle du quota

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_subscriptions        PRIMARY KEY (id),
    CONSTRAINT uq_gofp_sub_freelancer       UNIQUE (freelancer_actor_id)
);

ALTER TABLE gofp.subscriptions
    ADD CONSTRAINT chk_gofp_sub_type
    CHECK (subscription_type IN ('FREE','STANDARD','ADVANCE'));

ALTER TABLE gofp.subscriptions
    ADD CONSTRAINT chk_gofp_sub_status
    CHECK (status IN ('ACTIVE','SUSPENDED','EXPIRED','CANCELLED'));
