--liquibase formatted sql
--changeset MANFOUO_Braun:001_create_platform_clients_table
--comment: Create tnt_platform_clients table — one row per (platform, environment); replaces the .env-driven TntPlatformGatewayProperties registry

CREATE TABLE IF NOT EXISTS tnt_platform_clients (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    client_id       VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(120)    NOT NULL,
    platform_code   VARCHAR(40)     NOT NULL,
    environment     VARCHAR(16)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    description     TEXT,
    contact_email   VARCHAR(160),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_by      VARCHAR(80)
);

--rollback DROP TABLE IF EXISTS tnt_platform_clients;
