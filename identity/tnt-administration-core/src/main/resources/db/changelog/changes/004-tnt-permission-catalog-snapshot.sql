-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:004-tnt-permission-catalog-snapshot
-- comment: Adds tnt_permission_catalog_snapshot table to persist a point-in-time snapshot of the
--          enriched TntPermissionCatalog (). Tracks both legacy module:action permissions
--          and the new resource:action permissions from TntPermission (tnt-roles-core).
--          Also adds admin_provisioning_log table for tenant onboarding audit trail.

-- ============================================================
-- TABLE: tnt_permission_catalog_snapshot
-- Snapshot of the in-memory TntPermissionCatalog at startup.
-- Used for auditing which permissions were active when a role was provisioned.
-- ============================================================
CREATE TABLE IF NOT EXISTS administration.tnt_permission_catalog_snapshot (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    permission_code         VARCHAR(200)    NOT NULL,
    permission_name         VARCHAR(255)    NOT NULL,
    description             TEXT,
    module                  VARCHAR(100)    NOT NULL,
    scope                   VARCHAR(50)     NOT NULL,
    system_protected        BOOLEAN         NOT NULL DEFAULT FALSE,
    assignable              BOOLEAN         NOT NULL DEFAULT TRUE,
    -- Integration key to Kernel permission catalog (RT-comops-roles-core).
    -- Null for TNT-exclusive permissions with no Kernel counterpart.
    kernel_permission_id    UUID            NULL,
    -- Catalog version tag (e.g., "") — updated when catalog changes
    catalog_version         VARCHAR(20)     NOT NULL DEFAULT '',
    snapshot_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tnt_permission_catalog_code UNIQUE (permission_code)
);

CREATE INDEX IF NOT EXISTS idx_tnt_permission_catalog_module
    ON administration.tnt_permission_catalog_snapshot (module);

CREATE INDEX IF NOT EXISTS idx_tnt_permission_catalog_scope
    ON administration.tnt_permission_catalog_snapshot (scope);

COMMENT ON TABLE administration.tnt_permission_catalog_snapshot IS
    'Snapshot of TntPermissionCatalog  — includes both legacy module:action '
    'and new resource:action permissions from TntPermission (tnt-roles-core L1).';

-- ============================================================
-- TABLE: tnt_admin_provisioning_log
-- Audit trail for tenant onboarding role provisioning events.
-- Records when provisionForTenant() is called and what it produced.
-- ============================================================
CREATE TABLE IF NOT EXISTS administration.tnt_admin_provisioning_log (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    organization_id         UUID,
    actor_user_id           UUID,
    event_type              VARCHAR(100)    NOT NULL,
    -- JSON payload with provisioning details
    payload_json            TEXT            NOT NULL DEFAULT '{}',
    -- Whether tnt-roles-core canonical provisioning was also invoked
    roles_core_invoked      BOOLEAN         NOT NULL DEFAULT FALSE,
    provisioned_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_admin_provisioning_log_tenant
    ON administration.tnt_admin_provisioning_log (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tnt_admin_provisioning_log_event
    ON administration.tnt_admin_provisioning_log (event_type, provisioned_at DESC);

COMMENT ON TABLE administration.tnt_admin_provisioning_log IS
    'Audit trail for tenant onboarding role provisioning. '
    'roles_core_invoked=true means TntRoleInitializationService.provisionForTenant() was called.';

-- rollback DROP TABLE IF EXISTS administration.tnt_permission_catalog_snapshot;
-- rollback DROP TABLE IF EXISTS administration.tnt_admin_provisioning_log;
