-- liquibase formatted sql
-- changeset manfouo-braun:003-tnt-role-definitions
-- comment: TntRoleDefinition table — tracks provisioned TNT role templates per tenant
--          and their linkage to Kernel roles (RT-comops-roles-core, yow_kernel_db).

CREATE TABLE IF NOT EXISTS administration.tnt_role_definitions (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    template_code           VARCHAR(100)    NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    scope_type              VARCHAR(50)     NOT NULL,
    -- Comma-separated permission codes (e.g., "delivery:read,delivery:write").
    -- Stored as text for portability; the adapter handles serialization.
    permission_codes        TEXT            NOT NULL DEFAULT '',
    protected_definition    BOOLEAN         NOT NULL DEFAULT FALSE,
    -- Integration key → yow_kernel_db.roles.id (RT-comops-roles-core).
    -- NULL until the Kernel confirms role creation and the UUID is stored here.
    -- Logical reference only — no physical FK cross-database.
    kernel_role_id          UUID            NULL,
    -- True once the Kernel has confirmed role creation and kernel_role_id is populated.
    kernel_synced           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- A given template can only be provisioned once per tenant
    CONSTRAINT uq_tnt_role_def_tenant_template UNIQUE (tenant_id, template_code)
);

-- Fast lookup of all definitions for a tenant (used on every login/permission check)
CREATE INDEX IF NOT EXISTS idx_tnt_role_definitions_tenant_id
    ON administration.tnt_role_definitions (tenant_id);

-- Lookup by Kernel role UUID (used when Kernel publishes role-created events back)
CREATE INDEX IF NOT EXISTS idx_tnt_role_definitions_kernel_role_id
    ON administration.tnt_role_definitions (kernel_role_id)
    WHERE kernel_role_id IS NOT NULL;

-- Monitoring: find definitions still waiting for Kernel confirmation
CREATE INDEX IF NOT EXISTS idx_tnt_role_definitions_pending_sync
    ON administration.tnt_role_definitions (kernel_synced)
    WHERE kernel_synced = FALSE;

COMMENT ON TABLE administration.tnt_role_definitions IS
    'Tracks provisioned TiiBnTick role templates per tenant. '
    'kernel_role_id is a logical reference to yow_kernel_db.roles.id (RT-comops-roles-core) — '
    'no physical FK cross-database.';

COMMENT ON COLUMN administration.tnt_role_definitions.kernel_role_id IS
    'Logical reference to yow_kernel_db.roles.id (RT-comops-roles-core). '
    'Populated once the Kernel confirms role creation via TNT_ROLE_PROVISIONED event. '
    'No physical FK constraint — cross-database reference only.';

COMMENT ON COLUMN administration.tnt_role_definitions.kernel_synced IS
    'True when the Kernel has confirmed role creation and kernel_role_id is set. '
    'Definitions with kernel_synced=false are in a pending provisioning state.';

-- rollback DROP TABLE IF EXISTS administration.tnt_role_definitions;
