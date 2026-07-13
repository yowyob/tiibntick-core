-- liquibase formatted sql
-- changeset francois:gofp-008 labels:tnt-go-freelancer-point-back-core
-- comment: Besoins de livraison exprimés par les clients (avant annonce formelle)

CREATE TABLE IF NOT EXISTS gofp.delivery_needs (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    client_actor_id         UUID        NOT NULL,  -- → tnt-actor-core.client_profiles.actor_id
    packet_id               UUID,                  -- → gofp.packets.id (optionnel à la création)
    delivery_id             UUID,                  -- → gofp.deliveries.id (une fois assigné)

    -- Adresses libres (avant géocodage)
    pickup_street           VARCHAR(255),
    pickup_city             VARCHAR(100),
    pickup_district         VARCHAR(100),
    pickup_country          VARCHAR(100),
    pickup_latitude         DOUBLE PRECISION,
    pickup_longitude        DOUBLE PRECISION,

    delivery_street         VARCHAR(255),
    delivery_city           VARCHAR(100),
    delivery_district       VARCHAR(100),
    delivery_country        VARCHAR(100),
    delivery_latitude       DOUBLE PRECISION,
    delivery_longitude      DOUBLE PRECISION,

    title                   VARCHAR(255) NOT NULL,
    description             TEXT,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    duration                INTEGER,
    signature_url           VARCHAR(500),
    payment_method          VARCHAR(50),
    transport_method        VARCHAR(50),
    distance                DOUBLE PRECISION,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_delivery_needs       PRIMARY KEY (id),
    CONSTRAINT fk_gofp_dn_packet            FOREIGN KEY (packet_id)    REFERENCES gofp.packets(id),
    CONSTRAINT fk_gofp_dn_delivery          FOREIGN KEY (delivery_id)  REFERENCES gofp.deliveries(id)
);

ALTER TABLE gofp.delivery_needs
    ADD CONSTRAINT chk_gofp_dn_status
    CHECK (status IN ('OPEN','MATCHED','ASSIGNED','CANCELLED','EXPIRED'));
