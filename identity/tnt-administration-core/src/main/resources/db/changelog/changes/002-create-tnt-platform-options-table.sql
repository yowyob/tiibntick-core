-- liquibase formatted sql
-- changeset manfouo-braun:002-tnt-platform-options
-- comment: TiiBnTick-specific platform options per tenant
CREATE TABLE IF NOT EXISTS administration.tnt_platform_options (
    id                                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id                           UUID            NOT NULL UNIQUE,
    blockchain_enabled                  BOOLEAN         NOT NULL DEFAULT TRUE,
    smart_dispute_resolution_enabled    BOOLEAN         NOT NULL DEFAULT FALSE,
    blockchain_network                  VARCHAR(30)     NOT NULL DEFAULT 'PUBLIC_LITE',
    freelancer_mode_enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    require_freelancer_approval         BOOLEAN         NOT NULL DEFAULT TRUE,
    max_freelancer_concurrent_missions  INTEGER         NOT NULL DEFAULT 3,
    point_relais_mode_enabled           BOOLEAN         NOT NULL DEFAULT TRUE,
    relay_point_max_storage_hours       INTEGER         NOT NULL DEFAULT 72,
    announcement_marketplace_enabled    BOOLEAN         NOT NULL DEFAULT TRUE,
    max_courier_announcement_responses  INTEGER         NOT NULL DEFAULT 5,
    tva_rate                            NUMERIC(5,2)    NOT NULL DEFAULT 19.25,
    default_currency                    CHAR(3)         NOT NULL DEFAULT 'XAF',
    dispute_management_enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    dispute_filing_window_days          INTEGER         NOT NULL DEFAULT 7,
    created_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tnt_platform_options_tenant_id
    ON administration.tnt_platform_options (tenant_id);

COMMENT ON TABLE administration.tnt_platform_options IS
    'TiiBnTick-specific feature flags and configuration per tenant';
-- rollback DROP TABLE IF EXISTS administration.tnt_platform_options;
