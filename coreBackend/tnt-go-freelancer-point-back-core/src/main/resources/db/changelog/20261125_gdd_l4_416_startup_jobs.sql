-- Go-Freelancer de-duplication, lot L4 (gdd-l4-416): one row per one-off startup job (e.g. the backfill of the
-- legacy delivery_person_pricing grids into billing policies). The primary key makes a single instance own a job.
CREATE TABLE IF NOT EXISTS gofp_startup_jobs (
    name        VARCHAR(100) PRIMARY KEY,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    started_at  TIMESTAMPTZ  NOT NULL,
    finished_at TIMESTAMPTZ,
    details     TEXT
);
