package com.yowyob.tiibntick.core.roles.domain.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aggregated view of {@code tnt_role_sync_outbox} — row counts and the extremes of the
 * {@link RoleSyncStatus#DEAD DEAD} set — computed <strong>by the database</strong>, never by
 * loading rows into the application.
 *
 * <p>Exists because the monitoring path used to do the opposite. {@code
 * TntRoleSyncOutboxHealthIndicator} called {@code findByStatus} once per gauged status and
 * {@code collectList()}ed the result, purely to read {@code List::size}. On 27 November 2026
 * that meant materialising 1 324 000 rows — 921 816 {@code PENDING} and 401 733 {@code DEAD},
 * each carrying its JSON {@code payload} — every sixty seconds, to keep three integers up to
 * date. Three of the twelve R2DBC event loops were saturated converting those rows into
 * objects ({@code MappingR2dbcConverter.captureRowValues}), the heap climbed past 20 GiB, and
 * {@code /actuator/health} stopped answering within ninety seconds. The whole host was
 * degraded: unrelated services on the same machine went from 0.15 s to 1.2 s.
 *
 * <p>The cost of the old approach was proportional to the table; the cost of this one is not.
 *
 * @param countByStatus        number of rows per status — statuses with no row may be absent
 * @param deadCountByOperation number of {@code DEAD} rows per operation
 * @param oldestDeadCreatedAt  creation instant of the oldest {@code DEAD} row, {@code null} if none
 * @param oldestDeadLastError  {@code lastError} of that oldest {@code DEAD} row, {@code null} if none
 * @param newestDeadCreatedAt  creation instant of the newest {@code DEAD} row, {@code null} if none
 */
public record RoleSyncOutboxSummary(
        Map<RoleSyncStatus, Long> countByStatus,
        Map<RoleSyncOperation, Long> deadCountByOperation,
        LocalDateTime oldestDeadCreatedAt,
        String oldestDeadLastError,
        LocalDateTime newestDeadCreatedAt) {

    /** An empty outbox: every count zero, no DEAD extremes. */
    public static RoleSyncOutboxSummary empty() {
        return new RoleSyncOutboxSummary(Map.of(), Map.of(), null, null, null);
    }

    /** Rows currently in {@code status}, or zero when the status has none. */
    public long count(RoleSyncStatus status) {
        return countByStatus.getOrDefault(status, 0L);
    }

    /** Total number of {@code DEAD} rows. */
    public long deadCount() {
        return count(RoleSyncStatus.DEAD);
    }
}
