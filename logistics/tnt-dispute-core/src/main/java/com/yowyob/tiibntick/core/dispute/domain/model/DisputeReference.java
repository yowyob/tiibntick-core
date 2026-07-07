package com.yowyob.tiibntick.core.dispute.domain.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Value Object representing a human-readable, unique reference number for a dispute.
 *
 * <p>Format: {@code DSP-{YYYYMM}-{SEQUENCE}} — e.g. {@code DSP-202601-00042}
 *
 * <p>The reference is used for customer communication, legal documents, and
 * cross-platform tracking. It is immutable once generated.
 *
 * @author MANFOUO Braun
 */
public final class DisputeReference {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    private final String value;

    private DisputeReference(final String value) {
        this.value = Objects.requireNonNull(value, "DisputeReference value must not be null");
    }

    /**
     * Initializes the in-memory sequence counter from the persisted max value.
     * Must be called once at application startup (before any reference is generated)
     * to avoid duplicate key violations after restart.
     *
     * @param maxSequence the highest sequence number already in the database
     */
    public static void initSequence(int maxSequence) {
        SEQUENCE.set(maxSequence);
    }

    /**
     * Generates a new dispute reference using the current date and an auto-incrementing sequence.
     *
     * @return a new unique {@code DisputeReference}
     */
    public static DisputeReference generate() {
        final String month = LocalDateTime.now().format(MONTH_FORMAT);
        final int seq = SEQUENCE.incrementAndGet();
        return new DisputeReference(String.format("DSP-%s-%05d", month, seq));
    }

    /**
     * Reconstructs a {@code DisputeReference} from a persisted value.
     *
     * @param value the raw reference string
     * @return the corresponding {@code DisputeReference}
     */
    public static DisputeReference of(final String value) {
        return new DisputeReference(value);
    }

    /**
     * Returns the raw reference string.
     *
     * @return formatted reference, e.g. {@code DSP-202601-00042}
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeReference that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
