-- liquibase formatted sql
-- changeset tiibntick:create-relay-point-subscriptions

CREATE TABLE IF NOT EXISTS relay_point_subscriptions (
    id                UUID PRIMARY KEY,
    relay_point_id    UUID        NOT NULL,
    subscription_type VARCHAR(50) NOT NULL,
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    start_date        TIMESTAMP   NOT NULL,
    end_date          TIMESTAMP,
    price             FLOAT       NOT NULL,
    payment_method    VARCHAR(50) NOT NULL,
    deposits_used     INT         NOT NULL DEFAULT 0,
    reset_date        TIMESTAMP,
    CONSTRAINT fk_rp_sub_relay_point FOREIGN KEY (relay_point_id) REFERENCES logistics(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_relay_point_subscriptions_relay_point_id
    ON relay_point_subscriptions (relay_point_id);

COMMENT ON TABLE relay_point_subscriptions IS
    'Abonnements des Points Relais — contrôle quota de dépôts et taux de commission';
