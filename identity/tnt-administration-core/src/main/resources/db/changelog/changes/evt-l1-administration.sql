--liquibase formatted sql
-- Lot L1 « evt-seuils-plateforme » — seuils de livraison et de garde réglés par l'administrateur de TiiBnTick.
-- Sources : decisions-evenements-colis-livraison.md [B.5] et section J (QO-8, QO-18, QO-29, QO-30) ;
-- cible-evenements-colis-livraison.md §5.1. Réglage PLATEFORME (une seule ligne, pas par tenant), surchargeable
-- par organisation TiiBnTick Agency (id d'organisation Kernel unique). Additif et idempotent.

--changeset evt-l1:evt-l1-001-platform-delivery-thresholds labels:evt-l1
--comment: Platform-wide delivery, relay storage and late-alert thresholds (single row id = PLATFORM).
CREATE TABLE IF NOT EXISTS administration.tnt_delivery_thresholds (
    id                                   VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' PRIMARY KEY,
    max_delivery_attempts                INTEGER      NOT NULL DEFAULT 3,
    relay_storage_days                   INTEGER      NOT NULL DEFAULT 7,
    relay_storage_reminder_days_before   INTEGER      NOT NULL DEFAULT 1,
    max_failed_returns                   INTEGER      NOT NULL DEFAULT 3,
    pickup_late_grace_minutes            INTEGER      NOT NULL DEFAULT 15,
    delivery_late_grace_minutes          INTEGER      NOT NULL DEFAULT 0,
    delivery_late_fallback_hours_standard INTEGER     NOT NULL DEFAULT 48,
    delivery_late_fallback_hours_express INTEGER      NOT NULL DEFAULT 24,
    delivery_late_fallback_hours_same_day INTEGER     NOT NULL DEFAULT 8,
    eta_exceeded_grace_minutes           INTEGER      NOT NULL DEFAULT 30,
    gps_signal_lost_minutes              INTEGER      NOT NULL DEFAULT 10,
    prolonged_stop_minutes               INTEGER      NOT NULL DEFAULT 20,
    independent_relay_storage_min_days   INTEGER,
    independent_relay_storage_max_days   INTEGER,
    updated_by                           UUID,
    updated_at                           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_tnt_delivery_thresholds_singleton CHECK (id = 'PLATFORM'),
    CONSTRAINT ck_tnt_delivery_thresholds_attempts CHECK (max_delivery_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_tnt_delivery_thresholds_storage CHECK (relay_storage_days BETWEEN 1 AND 60),
    CONSTRAINT ck_tnt_delivery_thresholds_reminder CHECK (relay_storage_reminder_days_before >= 0
        AND relay_storage_reminder_days_before < relay_storage_days),
    CONSTRAINT ck_tnt_delivery_thresholds_failed_returns CHECK (max_failed_returns BETWEEN 1 AND 10),
    CONSTRAINT ck_tnt_delivery_thresholds_pickup_late CHECK (pickup_late_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_tnt_delivery_thresholds_delivery_late CHECK (delivery_late_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_tnt_delivery_thresholds_fallback_standard CHECK (delivery_late_fallback_hours_standard BETWEEN 1 AND 720),
    CONSTRAINT ck_tnt_delivery_thresholds_fallback_express CHECK (delivery_late_fallback_hours_express BETWEEN 1 AND 720),
    CONSTRAINT ck_tnt_delivery_thresholds_fallback_same_day CHECK (delivery_late_fallback_hours_same_day BETWEEN 1 AND 720),
    CONSTRAINT ck_tnt_delivery_thresholds_eta CHECK (eta_exceeded_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_tnt_delivery_thresholds_gps CHECK (gps_signal_lost_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_tnt_delivery_thresholds_stop CHECK (prolonged_stop_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_tnt_delivery_thresholds_relay_min CHECK (independent_relay_storage_min_days IS NULL
        OR independent_relay_storage_min_days BETWEEN 1 AND 60),
    CONSTRAINT ck_tnt_delivery_thresholds_relay_max CHECK (independent_relay_storage_max_days IS NULL
        OR independent_relay_storage_max_days BETWEEN 1 AND 60),
    CONSTRAINT ck_tnt_delivery_thresholds_relay_bounds CHECK (independent_relay_storage_min_days IS NULL
        OR independent_relay_storage_max_days IS NULL
        OR independent_relay_storage_min_days <= independent_relay_storage_max_days)
);

-- Valeurs décidées : [B.5] 3 tentatives, garde 7 j, rappel J-1 ; QO-8 3 retours échoués ; QO-18 alertes de retard
-- (enlèvement +15 min, livraison = fin de fenêtre, ETA +30 min, GPS 10 min, arrêt 20 min ; délai par urgence repris
-- de TntSettingsRegistrar : standard 48 h, express 24 h, jour même 8 h). Bornes QO-29 non fixées (NULL).
INSERT INTO administration.tnt_delivery_thresholds (id) VALUES ('PLATFORM') ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE administration.tnt_delivery_thresholds IS
    'Platform-wide delivery thresholds set by the TiiBnTick administrator (one row, id = PLATFORM, not per tenant).';
COMMENT ON COLUMN administration.tnt_delivery_thresholds.independent_relay_storage_min_days IS
    'QO-29: lowest storage duration an independent relay may set for itself, NULL = no bound set by the admin yet.';
COMMENT ON COLUMN administration.tnt_delivery_thresholds.independent_relay_storage_max_days IS
    'QO-29: highest storage duration an independent relay may set for itself, NULL = no bound set by the admin yet.';
--rollback DROP TABLE IF EXISTS administration.tnt_delivery_thresholds;

--changeset evt-l1:evt-l1-002-organization-delivery-thresholds labels:evt-l1
--comment: Per-organization (TiiBnTick Agency) overrides of the platform thresholds; NULL column = inherit.
CREATE TABLE IF NOT EXISTS administration.tnt_organization_delivery_thresholds (
    organization_id                      UUID         NOT NULL PRIMARY KEY,
    max_delivery_attempts                INTEGER,
    relay_storage_days                   INTEGER,
    relay_storage_reminder_days_before   INTEGER,
    max_failed_returns                   INTEGER,
    pickup_late_grace_minutes            INTEGER,
    delivery_late_grace_minutes          INTEGER,
    delivery_late_fallback_hours_standard INTEGER,
    delivery_late_fallback_hours_express INTEGER,
    delivery_late_fallback_hours_same_day INTEGER,
    eta_exceeded_grace_minutes           INTEGER,
    gps_signal_lost_minutes              INTEGER,
    prolonged_stop_minutes               INTEGER,
    updated_by                           UUID,
    updated_at                           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_tnt_org_delivery_thresholds_attempts CHECK (max_delivery_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_tnt_org_delivery_thresholds_storage CHECK (relay_storage_days BETWEEN 1 AND 60),
    CONSTRAINT ck_tnt_org_delivery_thresholds_reminder CHECK (relay_storage_reminder_days_before BETWEEN 0 AND 59),
    CONSTRAINT ck_tnt_org_delivery_thresholds_reminder_storage CHECK (relay_storage_reminder_days_before IS NULL
        OR relay_storage_days IS NULL OR relay_storage_reminder_days_before < relay_storage_days),
    CONSTRAINT ck_tnt_org_delivery_thresholds_failed_returns CHECK (max_failed_returns BETWEEN 1 AND 10),
    CONSTRAINT ck_tnt_org_delivery_thresholds_pickup_late CHECK (pickup_late_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_tnt_org_delivery_thresholds_delivery_late CHECK (delivery_late_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_tnt_org_delivery_thresholds_fallback_standard CHECK (delivery_late_fallback_hours_standard BETWEEN 1 AND 720),
    CONSTRAINT ck_tnt_org_delivery_thresholds_fallback_express CHECK (delivery_late_fallback_hours_express BETWEEN 1 AND 720),
    CONSTRAINT ck_tnt_org_delivery_thresholds_fallback_same_day CHECK (delivery_late_fallback_hours_same_day BETWEEN 1 AND 720),
    CONSTRAINT ck_tnt_org_delivery_thresholds_eta CHECK (eta_exceeded_grace_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_tnt_org_delivery_thresholds_gps CHECK (gps_signal_lost_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_tnt_org_delivery_thresholds_stop CHECK (prolonged_stop_minutes BETWEEN 1 AND 1440)
);

COMMENT ON TABLE administration.tnt_organization_delivery_thresholds IS
    'Overrides of the platform delivery thresholds for one TiiBnTick Agency organization (Kernel organization id), NULL = inherit the platform value.';
--rollback DROP TABLE IF EXISTS administration.tnt_organization_delivery_thresholds;

--changeset evt-l1:evt-l1-003-independent-relay-storage-default-bounds labels:evt-l1
--comment: Decision D-130 (QO-29): an independent relay may set its own storage duration between 3 and 30 days by default, adjustable by the TiiBnTick administrator. The bounds are always set from now on (no NULL = no bound). Not destructive: only NULL bounds are filled, a bound already set by the administrator is kept.
UPDATE administration.tnt_delivery_thresholds
   SET independent_relay_storage_min_days = COALESCE(independent_relay_storage_min_days,
           LEAST(3, COALESCE(independent_relay_storage_max_days, 3))),
       independent_relay_storage_max_days = COALESCE(independent_relay_storage_max_days,
           GREATEST(30, COALESCE(independent_relay_storage_min_days, 30)))
 WHERE independent_relay_storage_min_days IS NULL OR independent_relay_storage_max_days IS NULL;
ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_min_days SET DEFAULT 3;
ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_max_days SET DEFAULT 30;
ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_min_days SET NOT NULL;
ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_max_days SET NOT NULL;
COMMENT ON COLUMN administration.tnt_delivery_thresholds.independent_relay_storage_min_days IS
    'QO-29 / D-130: lowest storage duration an independent relay may set for itself (default 3 days).';
COMMENT ON COLUMN administration.tnt_delivery_thresholds.independent_relay_storage_max_days IS
    'QO-29 / D-130: highest storage duration an independent relay may set for itself (default 30 days).';
--rollback ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_min_days DROP NOT NULL;
--rollback ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_max_days DROP NOT NULL;
--rollback ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_min_days DROP DEFAULT;
--rollback ALTER TABLE administration.tnt_delivery_thresholds ALTER COLUMN independent_relay_storage_max_days DROP DEFAULT;
