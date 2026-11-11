--liquibase formatted sql
--changeset MANFOUO_Braun:tnt-i18n-001-create-user-language-preference
--comment: One language per account (Kernel user id), shared by every profile and every TiiBnTick product.

CREATE SCHEMA IF NOT EXISTS i18n;

CREATE TABLE IF NOT EXISTS i18n.user_language_preference (
    user_id     UUID         PRIMARY KEY,
    language    VARCHAR(10)  NOT NULL,
    source      VARCHAR(12)  NOT NULL DEFAULT 'USER',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_i18n_user_language_source CHECK (source IN ('USER', 'DETECTED'))
);

--rollback DROP TABLE IF EXISTS i18n.user_language_preference;
