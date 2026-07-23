package com.yowyob.tiibntick.core.dispute.domain.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Value Object representing a human-readable, unique reference number for a dispute.
 *
 * <p>Format: {@code DSP-{YYYYMM}-{SEQUENCE}} — e.g. {@code DSP-202601-00042}
 *
 * <p>The reference is used for customer communication, legal documents, and
 * cross-platform tracking. It is immutable once generated.
 *
 * <p><b>Sequence source (Chantier D · Audit n°6 · S1):</b> the {@code SEQUENCE} portion
 * must be supplied by the caller — see {@link #forSequence(long)} — obtained from the
 * {@code dispute_reference_seq} PostgreSQL sequence via
 * {@code IDisputeReferenceGenerator.nextReference()}. This value object used to generate
 * that sequence itself from a static {@code AtomicInteger}, which is scoped to a single
 * JVM: every application instance in a multi-instance deployment held its own independent
 * counter, seeded from the same persisted max at startup, so two instances handling
 * concurrent {@code openDispute()} calls were guaranteed to collide. A database sequence
 * is atomic across every connection/instance sharing it, which removes that failure mode.
 *
 * @author MANFOUO Braun
 */
public final class DisputeReference {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final String value;

    private DisputeReference(final String value) {
        this.value = Objects.requireNonNull(value, "DisputeReference value must not be null");
    }

    /**
     * Builds a new {@code DisputeReference} from a sequence value obtained from the
     * {@code dispute_reference_seq} PostgreSQL sequence (see
     * {@code IDisputeReferenceGenerator}), combined with the current year/month.
     *
     * @param sequence a value from {@code dispute_reference_seq} — must be unique and
     *                 positive; the caller (the sequence adapter) guarantees this, not
     *                 this value object
     * @return a new unique {@code DisputeReference}
     */
    public static DisputeReference forSequence(final long sequence) {
        final String month = LocalDateTime.now().format(MONTH_FORMAT);
        return new DisputeReference(String.format("DSP-%s-%05d", month, sequence));
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
