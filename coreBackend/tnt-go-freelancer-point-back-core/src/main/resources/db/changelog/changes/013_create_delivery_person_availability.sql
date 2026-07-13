-- liquibase formatted sql
-- changeset francois:gofp-013 labels:tnt-go-freelancer-point-back-core
-- comment: Disponibilité temps réel et planifiée des livreurs freelancers

CREATE TABLE IF NOT EXISTS gofp.delivery_person_availability (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id
    is_available            BOOLEAN     NOT NULL DEFAULT TRUE,
    availability_start      TIMESTAMPTZ,
    availability_end        TIMESTAMPTZ,

    -- Position courante (mise à jour en temps réel)
    current_lat             DOUBLE PRECISION,
    current_lon             DOUBLE PRECISION,
    location_updated_at     TIMESTAMPTZ,

    -- Zone de service actuelle
    service_zone_id         UUID,                  -- → tnt-geo-core.service_zones.id

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_dp_availability  PRIMARY KEY (id),
    CONSTRAINT uq_gofp_dp_avail         UNIQUE (freelancer_actor_id)
);
