--liquibase formatted sql
--changeset manfouo-braun:001-create-tnt-media-schema
--comment: Creates the tnt_media schema and the media_files table for tnt-media-core
--author: MANFOUO Braun

-- ─────────────────────────────────────────────────────────────────────────────
-- Create dedicated schema for tnt-media-core
-- ─────────────────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS tnt_media;

-- ─────────────────────────────────────────────────────────────────────────────
-- Main table: media_files
-- Stores metadata for all files managed by tnt-media-core.
-- File content itself lives in MinIO (bucket-per-tenant: tnt-{tenantId}).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tnt_media.media_files
(
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          VARCHAR(100)  NOT NULL,
    owner_user_id      VARCHAR(255),
    media_type         VARCHAR(50)   NOT NULL,
    mime_type          VARCHAR(100)  NOT NULL,
    original_file_name VARCHAR(500),
    storage_bucket     VARCHAR(255)  NOT NULL,
    storage_key        VARCHAR(1000) NOT NULL,
    size_bytes         BIGINT,
    sha256_hash        VARCHAR(64),
    is_public          BOOLEAN       NOT NULL DEFAULT FALSE,
    expires_at         TIMESTAMP WITHOUT TIME ZONE,
    uploaded_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    metadata_json      TEXT          NOT NULL DEFAULT '{}',

    CONSTRAINT pk_media_files PRIMARY KEY (id),
    CONSTRAINT uq_media_storage_key UNIQUE (storage_bucket, storage_key)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Indexes for common query patterns
-- ─────────────────────────────────────────────────────────────────────────────

-- Multi-tenant queries by tenant
CREATE INDEX IF NOT EXISTS idx_media_files_tenant_id
    ON tnt_media.media_files (tenant_id);

-- Filter by type within a tenant (e.g., find all QR_CODEs for a tenant)
CREATE INDEX IF NOT EXISTS idx_media_files_tenant_type
    ON tnt_media.media_files (tenant_id, media_type);

-- SHA-256 deduplication check (per tenant)
CREATE INDEX IF NOT EXISTS idx_media_files_hash_tenant
    ON tnt_media.media_files (sha256_hash, tenant_id)
    WHERE sha256_hash IS NOT NULL;

-- Cleanup scheduler: find expired files
CREATE INDEX IF NOT EXISTS idx_media_files_expires_at
    ON tnt_media.media_files (expires_at)
    WHERE expires_at IS NOT NULL;

-- Owner-based lookup
CREATE INDEX IF NOT EXISTS idx_media_files_owner_user
    ON tnt_media.media_files (owner_user_id)
    WHERE owner_user_id IS NOT NULL;

--rollback DROP TABLE IF EXISTS tnt_media.media_files;
--rollback DROP SCHEMA IF EXISTS tnt_media;
