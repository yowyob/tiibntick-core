-- liquibase formatted sql
-- changeset francois:gofp-003 labels:tnt-go-freelancer-point-back-core
-- comment: Annonces TiiBnPick — cycle de vie Market complet
-- Les identités (client, livreur) sont référencées par leur actorId du Kernel/tnt-actor-core.
-- Les adresses sont référencées par leur nodeId de tnt-geo-core.

CREATE TABLE IF NOT EXISTS gofp.announcements (
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- Références vers modules Core existants (pas de duplication)
    client_actor_id             UUID        NOT NULL,  -- → tnt-actor-core.client_profiles.actor_id
    pickup_address_node_id      VARCHAR(36),           -- → tnt-geo-core.road_nodes.id (optionnel si adresse libre)
    delivery_address_node_id    VARCHAR(36),           -- → tnt-geo-core.road_nodes.id (optionnel)
    relay_hub_id                UUID,                  -- → tnt-geo-core.relay_hubs.id (si passage par hub)
    destination_logistics_id    UUID,                  -- → gofp.relay_hub_extensions.id (point relais Market)

    -- Référence vers le colis (propre à L6)
    packet_id                   UUID        NOT NULL,  -- → gofp.packets.id

    -- Données de l'annonce (propres au produit Market)
    title                       VARCHAR(255) NOT NULL,
    description                 TEXT,
    status                      VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    amount                      DOUBLE PRECISION,
    logistics_price             DOUBLE PRECISION,
    distance                    DOUBLE PRECISION,
    duration                    INTEGER,
    transport_method            VARCHAR(50),
    payment_method              VARCHAR(50),
    signature_url               VARCHAR(500),
    required_vehicle_type       VARCHAR(50),
    auto_publish                BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Expéditeur (contact, peut différer du client)
    shipper_id                  UUID,        -- → gofp.contacts si on crée la table, sinon stocker les champs
    shipper_first_name          VARCHAR(100),
    shipper_last_name           VARCHAR(100),
    shipper_email               VARCHAR(255),
    shipper_phone               VARCHAR(50),

    -- Destinataire
    recipient_id                UUID,
    recipient_first_name        VARCHAR(100),
    recipient_last_name         VARCHAR(100),
    recipient_email             VARCHAR(255),
    recipient_phone             VARCHAR(50),

    -- Adresses libres (si non géocodées)
    pickup_street               VARCHAR(255),
    pickup_city                 VARCHAR(100),
    pickup_district             VARCHAR(100),
    pickup_country              VARCHAR(100),
    pickup_latitude             DOUBLE PRECISION,
    pickup_longitude            DOUBLE PRECISION,

    delivery_street             VARCHAR(255),
    delivery_city               VARCHAR(100),
    delivery_district           VARCHAR(100),
    delivery_country            VARCHAR(100),
    delivery_latitude           DOUBLE PRECISION,
    delivery_longitude          DOUBLE PRECISION,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_gofp_announcements PRIMARY KEY (id),
    CONSTRAINT fk_gofp_ann_packet     FOREIGN KEY (packet_id) REFERENCES gofp.packets(id)
);

-- Statuts valides : DRAFT, PUBLISHED, ASSIGNED, IN_PROGRESS, DELIVERED, CANCELLED, EXPIRED
ALTER TABLE gofp.announcements
    ADD CONSTRAINT chk_gofp_ann_status
    CHECK (status IN ('DRAFT','PUBLISHED','ASSIGNED','IN_PROGRESS','DELIVERED','CANCELLED','EXPIRED'));
