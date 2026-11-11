--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-001-create-message-catalog
--comment: Message catalog of yow-i18n-kernel — one row per (namespace, key, language, variant); live text + optional draft; every publication is kept in message_history.

CREATE SCHEMA IF NOT EXISTS i18n;

CREATE TABLE IF NOT EXISTS i18n.message (
    id          UUID         PRIMARY KEY,
    namespace   VARCHAR(40)  NOT NULL,
    msg_key     VARCHAR(200) NOT NULL,
    language    VARCHAR(10)  NOT NULL,
    variant     VARCHAR(20)  NOT NULL DEFAULT 'DEFAULT',
    text        TEXT,
    draft_text  TEXT,
    version     INTEGER      NOT NULL DEFAULT 0,
    updated_by  VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_i18n_message UNIQUE (namespace, msg_key, language, variant),
    CONSTRAINT ck_i18n_message_content CHECK (text IS NOT NULL OR draft_text IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_i18n_message_lang_ns ON i18n.message (language, namespace) WHERE text IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_i18n_message_key ON i18n.message (msg_key);

CREATE TABLE IF NOT EXISTS i18n.message_history (
    id            BIGSERIAL    PRIMARY KEY,
    message_id    UUID         NOT NULL REFERENCES i18n.message (id) ON DELETE CASCADE,
    version       INTEGER      NOT NULL,
    text          TEXT         NOT NULL,
    published_by  VARCHAR(120),
    published_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_i18n_message_history UNIQUE (message_id, version)
);

--rollback DROP TABLE IF EXISTS i18n.message_history; DROP TABLE IF EXISTS i18n.message; DROP SCHEMA IF EXISTS i18n;
