-- liquibase formatted sql
-- changeset francois:gofp-004 labels:tnt-go-freelancer-point-back-core
-- comment: Livraisons Market — FSM CREATED→PICKED_UP→IN_TRANSIT→DELIVERED|FAILED
-- Le livreur est référencé par son actorId de tnt-actor-core.freelancer_profiles.

CREATE TABLE IF NOT EXISTS gofp.deliveries (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- Références
    announcement_id         UUID        NOT NULL,  -- → gofp.announcements.id
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id
    relay_hub_id            UUID,                  -- → tnt-geo-core.relay_hubs.id (si passage par hub)

    -- État de la livraison
    status                  VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    urgency                 VARCHAR(30) NOT NULL DEFAULT 'NORMAL',

    -- Tarification
    tarif                   DOUBLE PRECISION,

    -- Fenêtres temporelles
    pickup_min_time         TIMESTAMPTZ,
    pickup_max_time         TIMESTAMPTZ,
    delivery_min_time       TIMESTAMPTZ,
    delivery_max_time       TIMESTAMPTZ,
    estimated_delivery      TIMESTAMPTZ,
    actual_pickup_time      TIMESTAMPTZ,
    actual_delivery_time    TIMESTAMPTZ,

    -- Métriques
    distance_km             DOUBLE PRECISION,
    duration_minutes        INTEGER,
    note_livreur            DOUBLE PRECISION,
    delivery_note           DOUBLE PRECISION,

    -- GPS au moment de la livraison
    delivery_lat            DOUBLE PRECISION,
    delivery_lon            DOUBLE PRECISION,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_deliveries           PRIMARY KEY (id),
    CONSTRAINT fk_gofp_del_announcement     FOREIGN KEY (announcement_id) REFERENCES gofp.announcements(id)
);

ALTER TABLE gofp.deliveries
    ADD CONSTRAINT chk_gofp_del_status
    CHECK (status IN ('CREATED','ASSIGNED','PICKED_UP','IN_TRANSIT','AT_RELAY','DELIVERED','FAILED','CANCELLED'));

ALTER TABLE gofp.deliveries
    ADD CONSTRAINT chk_gofp_del_urgency
    CHECK (urgency IN ('LOW','NORMAL','HIGH','URGENT'));
