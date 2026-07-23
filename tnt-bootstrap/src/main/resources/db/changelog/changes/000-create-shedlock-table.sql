--liquibase formatted sql

--changeset MANFOUO_Braun:000-create-shedlock-table
--comment: Chantier D (Audit n6 - S2). Cross-cutting table backing ShedLock's distributed
-- lock (net.javacrumbs.shedlock) — shared by every module's @Scheduled job across the
-- whole tnt-bootstrap-assembled monolith, hence owned here rather than by any single
-- domain module (see TntSchedulerLockConfig for the LockProvider bean). Column
-- definitions match ShedLock's own required schema exactly.
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

--rollback DROP TABLE IF EXISTS shedlock;
