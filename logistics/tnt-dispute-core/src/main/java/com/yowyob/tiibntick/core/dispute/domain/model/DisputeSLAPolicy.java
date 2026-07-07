package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object encapsulating the Service Level Agreement (SLA) policy for a dispute.
 *
 * <p>SLA deadlines vary by {@link DisputePriority}:
 * <ul>
 *   <li>CRITICAL — initial response within 24 h, resolution within 3 days</li>
 *   <li>HIGH      — initial response within 48 h, resolution within 5 days</li>
 *   <li>NORMAL    — initial response within 72 h, resolution within 10 days</li>
 *   <li>LOW        — initial response within 7 days, resolution within 21 days</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public final class DisputeSLAPolicy {

    /** Hours within which a mediator must be assigned after opening. */
    private final int initialResponseDeadlineHours;

    /** Days within which the full investigation must complete. */
    private final int investigationDeadlineDays;

    /** Days within which the dispute must be fully resolved. */
    private final int resolutionDeadlineDays;

    /** Days of mediation inactivity after which escalation is automatically triggered. */
    private final int escalationThresholdDays;

    private DisputeSLAPolicy(
            final int initialResponseDeadlineHours,
            final int investigationDeadlineDays,
            final int resolutionDeadlineDays,
            final int escalationThresholdDays) {
        this.initialResponseDeadlineHours = initialResponseDeadlineHours;
        this.investigationDeadlineDays = investigationDeadlineDays;
        this.resolutionDeadlineDays = resolutionDeadlineDays;
        this.escalationThresholdDays = escalationThresholdDays;
    }

    /**
     * Creates the appropriate SLA policy for a given priority level.
     *
     * @param priority the dispute priority
     * @return the corresponding {@code DisputeSLAPolicy}
     */
    public static DisputeSLAPolicy forPriority(final DisputePriority priority) {
        Objects.requireNonNull(priority, "Priority must not be null");
        return switch (priority) {
            case CRITICAL -> new DisputeSLAPolicy(24, 2, 3, 1);
            case HIGH     -> new DisputeSLAPolicy(48, 4, 5, 2);
            case NORMAL   -> new DisputeSLAPolicy(72, 7, 10, 4);
            case LOW      -> new DisputeSLAPolicy(168, 14, 21, 7);
        };
    }

    /**
     * Creates a {@code DisputeSLAPolicy} with fully custom parameters.
     *
     * @param initialResponseDeadlineHours hours until response is required
     * @param investigationDeadlineDays    days until investigation must finish
     * @param resolutionDeadlineDays       days until full resolution
     * @param escalationThresholdDays      days of inactivity before auto-escalation
     * @return the custom policy
     */
    public static DisputeSLAPolicy custom(
            final int initialResponseDeadlineHours,
            final int investigationDeadlineDays,
            final int resolutionDeadlineDays,
            final int escalationThresholdDays) {
        return new DisputeSLAPolicy(
                initialResponseDeadlineHours,
                investigationDeadlineDays,
                resolutionDeadlineDays,
                escalationThresholdDays);
    }

    /**
     * Computes the absolute response deadline from a given filing timestamp.
     *
     * @param filedAt the moment the dispute was filed
     * @return the deadline for initial mediator assignment
     */
    public LocalDateTime responseDeadline(final LocalDateTime filedAt) {
        return filedAt.plusHours(initialResponseDeadlineHours);
    }

    /**
     * Computes the absolute resolution deadline from a given filing timestamp.
     *
     * @param filedAt the moment the dispute was filed
     * @return the deadline for full resolution
     */
    public LocalDateTime resolutionDeadline(final LocalDateTime filedAt) {
        return filedAt.plusDays(resolutionDeadlineDays);
    }

    /**
     * Checks whether the initial response SLA has been breached.
     *
     * @param filedAt the moment the dispute was filed
     * @param now     the current moment
     * @return {@code true} if the response deadline has passed
     */
    public boolean isResponseBreached(final LocalDateTime filedAt, final LocalDateTime now) {
        return now.isAfter(responseDeadline(filedAt));
    }

    /**
     * Checks whether the global resolution SLA has been breached.
     *
     * @param filedAt the moment the dispute was filed
     * @param now     the current moment
     * @return {@code true} if the resolution deadline has passed
     */
    public boolean isResolutionBreached(final LocalDateTime filedAt, final LocalDateTime now) {
        return now.isAfter(resolutionDeadline(filedAt));
    }

    public int getInitialResponseDeadlineHours() {
        return initialResponseDeadlineHours;
    }

    public int getInvestigationDeadlineDays() {
        return investigationDeadlineDays;
    }

    public int getResolutionDeadlineDays() {
        return resolutionDeadlineDays;
    }

    public int getEscalationThresholdDays() {
        return escalationThresholdDays;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeSLAPolicy that)) return false;
        return initialResponseDeadlineHours == that.initialResponseDeadlineHours
                && investigationDeadlineDays == that.investigationDeadlineDays
                && resolutionDeadlineDays == that.resolutionDeadlineDays
                && escalationThresholdDays == that.escalationThresholdDays;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                initialResponseDeadlineHours,
                investigationDeadlineDays,
                resolutionDeadlineDays,
                escalationThresholdDays);
    }

    @Override
    public String toString() {
        return "DisputeSLAPolicy{response=%dh, investigation=%dd, resolution=%dd, escalation=%dd}"
                .formatted(initialResponseDeadlineHours, investigationDeadlineDays,
                        resolutionDeadlineDays, escalationThresholdDays);
    }
}
