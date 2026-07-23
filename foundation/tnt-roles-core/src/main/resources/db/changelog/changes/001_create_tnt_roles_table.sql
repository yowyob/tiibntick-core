--liquibase formatted sql
--changeset MANFOUO_Braun:001_create_tnt_roles_table
--comment: Create tnt_roles table — local persistence for provisioned RBAC roles (Chantier D · Audit n°6 · S5)

CREATE TABLE IF NOT EXISTS tnt_roles (
    id              UUID            NOT NULL PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(64)     NOT NULL,
    name            VARCHAR(120)    NOT NULL,
    scope_type      VARCHAR(16)     NOT NULL,
    permissions     TEXT            NOT NULL DEFAULT '',
    system_role     BOOLEAN         NOT NULL DEFAULT false,
    editable        BOOLEAN         NOT NULL DEFAULT true,
    kernel_role_id  UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

--rollback DROP TABLE IF EXISTS tnt_roles;
