--liquibase formatted sql
--changeset MANFOUO_Braun:003-add-tenant-context
--comment: Adds tenant/organization scoping to tnt_notifications and tnt_preference_notifications,
--comment: required to propagate X-Tenant-Id / X-Organization-Id to the Kernel notification engine.

ALTER TABLE tnt_notifications
    ADD COLUMN IF NOT EXISTS tenant_id       VARCHAR(36),
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_tnt_notifications_tenant ON tnt_notifications (tenant_id);

ALTER TABLE tnt_preference_notifications
    ADD COLUMN IF NOT EXISTS tenant_id       VARCHAR(36),
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_tnt_pref_notifications_tenant ON tnt_preference_notifications (tenant_id);

--rollback ALTER TABLE tnt_preference_notifications DROP COLUMN IF EXISTS tenant_id;
--rollback ALTER TABLE tnt_preference_notifications DROP COLUMN IF EXISTS organization_id;
--rollback ALTER TABLE tnt_notifications DROP COLUMN IF EXISTS tenant_id;
--rollback ALTER TABLE tnt_notifications DROP COLUMN IF EXISTS organization_id;
