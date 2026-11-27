--liquibase formatted sql
--changeset savhel:004_index_tnt_role_sync_outbox_reads
--comment: Index the three reads this table actually serves. Measured on production (27 Nov 2026, 1 364 000 rows): the poller's claim query ran as Gather Merge → Sort → Parallel Seq Scan at cost 81 980 EVERY TWO SECONDS, because idx_outbox_pending(status, next_attempt_at) cannot satisfy ORDER BY created_at; findByStatus('DEAD') ran as a Seq Scan at cost 75 280; findByAggregateId had no index at all. Author id is the committer, not the module owner — MANFOUO Braun owns this schema and has not reviewed it.

-- The claim query: WHERE status IN ('PENDING','RETRYING') AND next_attempt_at <= now()
--                 ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED.
-- created_at leads so the index walks in the required order and the LIMIT can stop early;
-- next_attempt_at follows so the due-date test is answered from the index. The status
-- predicate lives in the WHERE clause, which is why status is not a key column.
CREATE INDEX IF NOT EXISTS idx_outbox_claim
    ON tnt_role_sync_outbox (created_at, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- The monitoring aggregates: GROUP BY status, and min/max(created_at) over the DEAD rows.
-- Lets both run as index-only scans instead of reading the heap.
CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON tnt_role_sync_outbox (status, created_at);

-- findByAggregateId: one role's or assignment's full sync history. Had no index.
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON tnt_role_sync_outbox (aggregate_id);

-- idx_outbox_pending (changeset 003) served only the claim query, and idx_outbox_claim
-- serves it strictly better. Its sole remaining effect is write amplification on a table
-- with 921 816 rows in the states it covers, so it goes. Changeset 003 itself is untouched.
DROP INDEX IF EXISTS idx_outbox_pending;

--rollback DROP INDEX IF EXISTS idx_outbox_aggregate;
--rollback DROP INDEX IF EXISTS idx_outbox_status_created;
--rollback DROP INDEX IF EXISTS idx_outbox_claim;
--rollback CREATE INDEX IF NOT EXISTS idx_outbox_pending ON tnt_role_sync_outbox(status, next_attempt_at) WHERE status IN ('PENDING', 'RETRYING');
