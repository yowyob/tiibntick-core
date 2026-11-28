package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.StartupJobLockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * {@link StartupJobLockPort} on {@code gofp_startup_jobs} (changeset {@code gdd-l4-416}): the primary key on the job
 * name lets a single instance insert or re-take the row, atomically, in one statement.
 */
@Component
@RequiredArgsConstructor
public class R2dbcStartupJobLock implements StartupJobLockPort {

    private final DatabaseClient db;

    @Override
    public Mono<Boolean> claim(String job, Duration staleAfter) {
        return db.sql("""
                        INSERT INTO gofp_startup_jobs (name, status, started_at)
                        VALUES (:name, 'RUNNING', now())
                        ON CONFLICT (name) DO UPDATE
                           SET status = 'RUNNING', started_at = now(), finished_at = NULL, details = NULL
                         WHERE gofp_startup_jobs.status = 'FAILED'
                            OR (gofp_startup_jobs.status = 'RUNNING'
                                AND gofp_startup_jobs.started_at < now() - make_interval(secs => :staleSeconds))
                        RETURNING name""")
                .bind("name", job)
                .bind("staleSeconds", (double) staleAfter.toSeconds())
                .map((row, meta) -> row.get("name", String.class))
                .all()
                .hasElements();
    }

    @Override
    public Mono<Void> complete(String job, String details) {
        return finish(job, "DONE", details);
    }

    @Override
    public Mono<Void> fail(String job, String details) {
        return finish(job, "FAILED", details);
    }

    private Mono<Void> finish(String job, String status, String details) {
        return db.sql("UPDATE gofp_startup_jobs SET status = :status, finished_at = now(), details = :details "
                        + "WHERE name = :name")
                .bind("status", status)
                .bind("details", details == null ? "" : details)
                .bind("name", job)
                .fetch().rowsUpdated().then();
    }
}
