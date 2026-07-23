--liquibase formatted sql

--changeset MANFOUO_Braun:009_create_dispute_reference_sequence
--comment: Chantier D (Audit n6 - S1). The dispute reference numbering used to live in a
-- static AtomicInteger inside DisputeReference — one independent counter per JVM, so two
-- instances handling openDispute() concurrently were guaranteed to hand out the same
-- sequence number sooner or later. A PostgreSQL sequence is atomic across every
-- connection/instance sharing the database, which is exactly the guarantee needed here.
CREATE SEQUENCE IF NOT EXISTS dispute_reference_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

--rollback DROP SEQUENCE IF EXISTS dispute_reference_seq;

--changeset MANFOUO_Braun:009_seed_dispute_reference_sequence
--comment: Seed the sequence above the highest sequence number already embedded in an
-- existing tnt_disputes.reference (format DSP-YYYYMM-NNNNN), so this is safe to run
-- against a database that already has dispute rows created by the old in-memory
-- counter — numbering continues rather than restarting at 1 and colliding.
SELECT setval('dispute_reference_seq',
    (SELECT COALESCE(MAX(CAST(SPLIT_PART(reference, '-', 3) AS BIGINT)), 0) FROM tnt_disputes) + 1,
    false);

--rollback SELECT 1;
