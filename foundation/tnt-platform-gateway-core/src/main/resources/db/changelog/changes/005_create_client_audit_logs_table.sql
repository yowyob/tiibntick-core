--liquibase formatted sql
--changeset MANFOUO_Braun:005_create_client_audit_logs_table
--comment: Create tnt_client_audit_logs table — one row per gateway request, success or failure (Postgres-only, decided 2026-07-08)

CREATE TABLE IF NOT EXISTS tnt_client_audit_logs (
    id                    VARCHAR(36)   NOT NULL PRIMARY KEY,
    platform_client_id    VARCHAR(36)   REFERENCES tnt_platform_clients(id),
    client_id_attempted   VARCHAR(64),
    endpoint              VARCHAR(200)  NOT NULL,
    http_method           VARCHAR(10)   NOT NULL,
    outcome               VARCHAR(24)   NOT NULL,
    ip_address            VARCHAR(45),
    user_agent            VARCHAR(255),
    occurred_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tnt_client_audit_logs_client ON tnt_client_audit_logs(platform_client_id, occurred_at);

--rollback DROP TABLE IF EXISTS tnt_client_audit_logs;
