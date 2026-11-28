package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * End of storage of a parcel at its destination relay (QO-30, D-124): the shortest of the duration the client asked
 * for ({@code requestedStorageDays} of the announcement) and the relay's storage duration — the client can shorten
 * the storage, never lengthen it for free. A missing or non-positive request is ignored.
 *
 * <p>The reminder ([B.5], J-1) is due {@code reminderDaysBefore} days before the end; it may fall at or before the
 * deposit when the storage is very short — the relay storage scheduler (lot L2, spec §5.2) then sends it on its first
 * run. {@code reminderAt} is {@code null} when the reminder is disabled ({@code reminderDaysBefore = 0}).
 *
 * @param depositedAt           deposit time at the relay
 * @param relayStorageDays      the relay's storage duration (resolved)
 * @param requestedStorageDays  the client's request, or {@code null}
 * @param effectiveStorageDays  {@code min(request, relay)}
 * @param clientRequestApplied  whether the client's request shortened the storage
 * @param expiresAt             end of storage
 * @param reminderAt            reminder due time, or {@code null}
 */
public record RelayStorageDeadline(
        Instant depositedAt,
        int relayStorageDays,
        Integer requestedStorageDays,
        int effectiveStorageDays,
        boolean clientRequestApplied,
        Instant expiresAt,
        Instant reminderAt) {

    /**
     * @param depositedAt          deposit time at the relay
     * @param relayStorageDays     the relay's storage duration in days (≥ 1)
     * @param requestedStorageDays the client's request in days, or {@code null}
     * @param reminderDaysBefore   days before the end the recipient is reminded (0 = no reminder)
     */
    public static RelayStorageDeadline compute(Instant depositedAt, int relayStorageDays,
                                               Integer requestedStorageDays, int reminderDaysBefore) {
        Objects.requireNonNull(depositedAt, "depositedAt");
        if (relayStorageDays < 1) {
            throw new IllegalArgumentException("relayStorageDays must be at least 1: " + relayStorageDays);
        }
        if (reminderDaysBefore < 0) {
            throw new IllegalArgumentException("reminderDaysBefore must not be negative: " + reminderDaysBefore);
        }
        boolean applied = requestedStorageDays != null && requestedStorageDays > 0
                && requestedStorageDays < relayStorageDays;
        int effective = applied ? requestedStorageDays : relayStorageDays;
        Instant expiresAt = depositedAt.plus(Duration.ofDays(effective));
        Instant reminderAt = reminderDaysBefore == 0 ? null : expiresAt.minus(Duration.ofDays(reminderDaysBefore));
        return new RelayStorageDeadline(depositedAt, relayStorageDays, requestedStorageDays, effective, applied,
                expiresAt, reminderAt);
    }
}
