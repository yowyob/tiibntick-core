--liquibase formatted sql
--changeset MANFOUO_Braun:008_add_evidence_hash_to_dispute_evidences
--comment: Add evidence_hash column to tnt_dispute_evidences — client-supplied SHA-256 hash
-- of the evidence content, enabling real cryptographic verification of anchored proofs
-- via IBlockchainProofPort.verifyProof(blockchainRef, expectedHash).

ALTER TABLE tnt_dispute_evidences
    ADD COLUMN IF NOT EXISTS evidence_hash VARCHAR(64);

--rollback ALTER TABLE tnt_dispute_evidences DROP COLUMN IF EXISTS evidence_hash;
