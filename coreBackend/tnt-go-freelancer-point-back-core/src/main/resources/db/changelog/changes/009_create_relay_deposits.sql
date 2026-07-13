-- liquibase formatted sql
-- changeset francois:gofp-009 labels:tnt-go-freelancer-point-back-core
-- comment: Dépôts de colis en point relais (hub physique)

CREATE TABLE IF NOT EXISTS gofp.relay_deposits (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    packet_id               UUID        NOT NULL,  -- → gofp.packets.id
    delivery_id             UUID,                  -- → gofp.deliveries.id (si lié à une livraison)
    client_actor_id         UUID        NOT NULL,  -- → tnt-actor-core.client_profiles.actor_id
    relay_hub_id            UUID        NOT NULL,  -- → tnt-geo-core.relay_hubs.id
    freelancer_actor_id     UUID,                  -- → tnt-actor-core.freelancer_profiles.actor_id (déposant)

    status                  VARCHAR(30) NOT NULL DEFAULT 'DEPOSITED',
    storage_fee             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    penalty_fee             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    grace_period_days       INTEGER      NOT NULL DEFAULT 0,

    deposited_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expected_retrieval_at   TIMESTAMPTZ,
    retrieved_at            TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_relay_deposits       PRIMARY KEY (id),
    CONSTRAINT fk_gofp_rd_packet            FOREIGN KEY (packet_id)   REFERENCES gofp.packets(id),
    CONSTRAINT fk_gofp_rd_delivery          FOREIGN KEY (delivery_id) REFERENCES gofp.deliveries(id)
);

ALTER TABLE gofp.relay_deposits
    ADD CONSTRAINT chk_gofp_rd_status
    CHECK (status IN ('DEPOSITED','RETRIEVED','PENDING_RETRIEVAL','LOST','DAMAGED'));
