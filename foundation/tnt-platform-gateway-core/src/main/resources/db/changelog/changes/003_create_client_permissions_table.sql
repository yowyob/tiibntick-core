--liquibase formatted sql
--changeset MANFOUO_Braun:003_create_client_permissions_table
--comment: Create tnt_client_permissions table — granted scopes in the shared resource:action format (PermissionMatcher)

CREATE TABLE IF NOT EXISTS tnt_client_permissions (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    platform_client_id  VARCHAR(36)     NOT NULL REFERENCES tnt_platform_clients(id),
    scope               VARCHAR(60)     NOT NULL,
    granted_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    granted_by          VARCHAR(80),
    CONSTRAINT uq_tnt_client_permissions_client_scope UNIQUE (platform_client_id, scope)
);

--rollback DROP TABLE IF EXISTS tnt_client_permissions;
