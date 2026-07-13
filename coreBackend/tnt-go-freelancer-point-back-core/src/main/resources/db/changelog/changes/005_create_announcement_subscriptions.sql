-- liquibase formatted sql
-- changeset francois:gofp-005 labels:tnt-go-freelancer-point-back-core
-- comment: Candidatures/enchères des livreurs sur les annonces (TiiBnPick)

CREATE TABLE IF NOT EXISTS gofp.announcement_subscriptions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    announcement_id         UUID        NOT NULL,  -- → gofp.announcements.id
    freelancer_actor_id     UUID        NOT NULL,  -- → tnt-actor-core.freelancer_profiles.actor_id
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    proposed_price          DOUBLE PRECISION,
    message                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_ann_subs         PRIMARY KEY (id),
    CONSTRAINT fk_gofp_ann_subs_ann     FOREIGN KEY (announcement_id) REFERENCES gofp.announcements(id),
    CONSTRAINT uq_gofp_ann_subs         UNIQUE (announcement_id, freelancer_actor_id)
);

ALTER TABLE gofp.announcement_subscriptions
    ADD CONSTRAINT chk_gofp_ann_subs_status
    CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','WITHDRAWN'));
