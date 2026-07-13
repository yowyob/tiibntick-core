-- liquibase formatted sql
-- changeset francois:gofp-002 labels:tnt-go-freelancer-point-back-core
-- comment: Colis transportés dans le cadre du produit Market

CREATE TABLE IF NOT EXISTS gofp.packets (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    weight          DOUBLE PRECISION,
    width           DOUBLE PRECISION,
    height          DOUBLE PRECISION,
    length          DOUBLE PRECISION,
    thickness       DOUBLE PRECISION,
    fragile         BOOLEAN     NOT NULL DEFAULT FALSE,
    is_perishable   BOOLEAN     NOT NULL DEFAULT FALSE,
    designation     VARCHAR(255),
    description     TEXT,
    photo_packet    VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_packets PRIMARY KEY (id)
);
