--liquibase formatted sql
--changeset MANFOUO_Braun:004_create_api_key_rotation_history_table
--comment: Create tnt_api_key_rotation_history table — audit trail of key rotations

CREATE TABLE IF NOT EXISTS tnt_api_key_rotation_history (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    platform_client_id  VARCHAR(36)     NOT NULL REFERENCES tnt_platform_clients(id),
    old_api_key_id      VARCHAR(36)     REFERENCES tnt_api_keys(id),
    new_api_key_id      VARCHAR(36)     NOT NULL REFERENCES tnt_api_keys(id),
    rotated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    rotated_by          VARCHAR(80),
    reason              VARCHAR(255)
);

--rollback DROP TABLE IF EXISTS tnt_api_key_rotation_history;
