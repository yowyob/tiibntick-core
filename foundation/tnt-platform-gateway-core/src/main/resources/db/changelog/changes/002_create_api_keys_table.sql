--liquibase formatted sql
--changeset MANFOUO_Braun:002_create_api_keys_table
--comment: Create tnt_api_keys table — hash-only (BCrypt), prefix-indexed, supports overlapping ACTIVE keys during rotation

CREATE TABLE IF NOT EXISTS tnt_api_keys (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    platform_client_id  VARCHAR(36)     NOT NULL REFERENCES tnt_platform_clients(id),
    key_prefix          VARCHAR(24)     NOT NULL,
    key_hash            VARCHAR(255)    NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    expires_at          TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ,
    revoked_by          VARCHAR(80),
    revoked_reason      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_tnt_api_keys_client_prefix ON tnt_api_keys(platform_client_id, key_prefix);
CREATE INDEX IF NOT EXISTS idx_tnt_api_keys_status ON tnt_api_keys(status);

--rollback DROP TABLE IF EXISTS tnt_api_keys;
