-- ============================================================
-- Changeset 002: DID Documents table
-- Author: MANFOUO Braun | Module: tnt-trust
-- ============================================================
-- liquibase formatted sql
-- changeset manfouo_braun:002-create-did-documents

CREATE TABLE IF NOT EXISTS tnt_trust.did_documents (
    did                  VARCHAR(200)   NOT NULL,
    actor_id             VARCHAR(36)    NOT NULL,
    tenant_id            VARCHAR(36)    NOT NULL,
    public_key_pem       TEXT           NOT NULL,
    service_endpoint     VARCHAR(500)   NULL,
    issued_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMPTZ    NOT NULL,
    blockchain_tx_hash   VARCHAR(66)    NULL,
    revoked              BOOLEAN        NOT NULL DEFAULT FALSE,
    revoked_at           TIMESTAMPTZ    NULL,

    CONSTRAINT pk_did_documents PRIMARY KEY (did),
    CONSTRAINT chk_did_format CHECK (did LIKE 'did:tiibntick:%')
);

COMMENT ON TABLE tnt_trust.did_documents IS
    'Local cache of DID documents issued to TiiBnTick actors. '
    'Reflects the on-chain state from Hyperledger Fabric.';
COMMENT ON COLUMN tnt_trust.did_documents.did IS
    'DID string — format: did:tiibntick:{tenantId}:{actorId}';
COMMENT ON COLUMN tnt_trust.did_documents.blockchain_tx_hash IS
    'Fabric tx hash confirming on-chain anchoring — populated asynchronously';

-- rollback DROP TABLE IF EXISTS tnt_trust.did_documents;
