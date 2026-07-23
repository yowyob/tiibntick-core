--liquibase formatted sql
--changeset MANFOUO_Braun:002_create_tnt_user_role_assignments_table
--comment: Create tnt_user_role_assignments table — local persistence for user/role assignments (Chantier D · Audit n°6 · S5)

CREATE TABLE IF NOT EXISTS tnt_user_role_assignments (
    id                      UUID            NOT NULL PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    user_id                 UUID            NOT NULL,
    role_id                 UUID            NOT NULL REFERENCES tnt_roles(id),
    scope_type              VARCHAR(16)     NOT NULL,
    scope_id                UUID            NOT NULL,
    kernel_assignment_id    UUID,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, user_id, role_id, scope_type, scope_id)
);

CREATE INDEX idx_tura_tenant_user ON tnt_user_role_assignments(tenant_id, user_id);

--rollback DROP TABLE IF EXISTS tnt_user_role_assignments;
