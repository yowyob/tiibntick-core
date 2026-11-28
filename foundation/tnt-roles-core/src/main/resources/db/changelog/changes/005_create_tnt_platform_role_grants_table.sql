--liquibase formatted sql
--changeset platform-admins:005_create_tnt_platform_role_grants_table
--comment: Registry of the platform roles (TNT_ADMIN only today), decisions D-159 to D-175 (docs/architecture/chantiers/registre-administrateurs-plateforme.md). Read by the token subject whatever the tenant of the login; a revocation never deletes a row (history). Additive only.

CREATE TABLE IF NOT EXISTS tnt_platform_role_grants (
    id                      UUID            NOT NULL PRIMARY KEY,
    role_code               VARCHAR(32)     NOT NULL,
    user_id                 UUID            NOT NULL,
    grant_kind              VARCHAR(16)     NOT NULL,
    granted_by              UUID            NOT NULL,
    source_tenant_id        UUID            NOT NULL,
    system_tenant_at_grant  UUID            NOT NULL,
    granted_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    revoked_at              TIMESTAMPTZ,
    revoked_by              UUID,
    revoke_reason           VARCHAR(500),
    revoke_batch_id         UUID,
    CONSTRAINT ck_tprg_role_code CHECK (role_code IN ('TNT_ADMIN')),
    CONSTRAINT ck_tprg_grant_kind CHECK (grant_kind IN ('SYSTEM_OWNER', 'ASSIGNED')),
    CONSTRAINT ck_tprg_revocation CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

-- One active grant per role and account; a new grant after a revocation is a new row.
CREATE UNIQUE INDEX IF NOT EXISTS ux_tprg_active
    ON tnt_platform_role_grants (role_code, user_id)
    WHERE revoked_at IS NULL;

-- The listing (newest first) and the mass revocation (active grants whose source tenant is not the system tenant).
CREATE INDEX IF NOT EXISTS idx_tprg_role_granted ON tnt_platform_role_grants (role_code, granted_at DESC);
