-- liquibase formatted sql
-- changeset manfouo-braun:001-create-notify-schema

-- ─── Notifications table ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tnt_notifications (
    id               VARCHAR(36)  PRIMARY KEY,
    destinataire_id  VARCHAR(36)  NOT NULL,
    canal            VARCHAR(50)  NOT NULL,
    contenu          TEXT         NOT NULL,
    statut           VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    priorite         VARCHAR(20)  NOT NULL DEFAULT 'NORMALE',
    tentatives       INTEGER      NOT NULL DEFAULT 0,
    date_creation    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    date_envoi       TIMESTAMP WITH TIME ZONE,
    message_erreur   TEXT
);

CREATE INDEX IF NOT EXISTS idx_tnt_notif_destinataire ON tnt_notifications(destinataire_id);
CREATE INDEX IF NOT EXISTS idx_tnt_notif_statut       ON tnt_notifications(statut);
CREATE INDEX IF NOT EXISTS idx_tnt_notif_canal        ON tnt_notifications(canal);
CREATE INDEX IF NOT EXISTS idx_tnt_notif_priorite     ON tnt_notifications(priorite);
CREATE INDEX IF NOT EXISTS idx_tnt_notif_date_creation ON tnt_notifications(date_creation DESC);

-- ─── User notification preferences table ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS tnt_preference_notifications (
    utilisateur_id          VARCHAR(36)  PRIMARY KEY,
    canals_actifs_csv       VARCHAR(500) DEFAULT 'PUSH_FCM,SMS_LOCAL,WHATSAPP,EMAIL,IN_APP_WEBSOCKET',
    langue_preferee         VARCHAR(20)  NOT NULL DEFAULT 'fr_CM',
    notifications_activees  BOOLEAN      NOT NULL DEFAULT TRUE
);

-- rollback DROP TABLE IF EXISTS tnt_preference_notifications;
-- rollback DROP TABLE IF EXISTS tnt_notifications;
