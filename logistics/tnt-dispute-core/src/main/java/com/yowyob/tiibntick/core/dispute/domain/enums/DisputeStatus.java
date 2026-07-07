package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Enumeration of all possible states in a dispute's lifecycle.
 * Transitions are strictly enforced by the {@code Dispute} aggregate root.
 *
 * <p>State machine:
 * <pre>
 * OPEN → UNDER_INVESTIGATION → AWAITING_EVIDENCE ↔ UNDER_INVESTIGATION
 *                            → MEDIATION_IN_PROGRESS → PENDING_ARBITRATION
 *                                                    → PENDING_COMPENSATION → COMPENSATED
 *                                                    → CLOSED_RESOLVED
 *     → CLOSED_WITHDRAWN (any non-terminal state)
 *     → CLOSED_EXPIRED   (any non-terminal state via SLA breach)
 * </pre>
 *
 * @author MANFOUO Braun
 */
public enum DisputeStatus {

    /**
     * Dispute has been filed. SLA response timer is active.
     * Awaiting mediator assignment.
     */
    OPEN,

    /**
     * Mediator has been assigned. Investigation is in progress.
     * Blockchain proofs and evidence are being collected.
     */
    UNDER_INVESTIGATION,

    /**
     * Additional evidence has been requested from one or both parties.
     * A submission deadline is active.
     */
    AWAITING_EVIDENCE,

    /**
     * Active mediation session between claimant and respondent.
     * GPS traces, photos, blockchain proofs are being analyzed.
     */
    MEDIATION_IN_PROGRESS,

    /**
     * Mediation has failed or the dispute amount exceeded the escalation threshold.
     * Escalated to a higher arbitration authority (TiiBnTick Admin).
     */
    PENDING_ARBITRATION,

    /**
     * A ruling granting compensation has been issued.
     * Awaiting payment processing (wallet credit / Mobile Money).
     */
    PENDING_COMPENSATION,

    /**
     * Compensation has been successfully paid to the beneficiary.
     * Terminal state.
     */
    COMPENSATED,

    /**
     * Dispute closed without compensation (claim dismissed or mutual agreement).
     * Terminal state.
     */
    CLOSED_RESOLVED,

    /**
     * Dispute withdrawn voluntarily by the claimant before a ruling.
     * Terminal state.
     */
    CLOSED_WITHDRAWN,

    /**
     * Dispute automatically closed because the global SLA was breached with no action.
     * Terminal state.
     */
    CLOSED_EXPIRED;

    /**
     * Determines whether this status represents a terminal (final) state.
     * No transitions are allowed from terminal states.
     *
     * @return {@code true} if the dispute cannot transition further
     */
    public boolean isTerminal() {
        return this == COMPENSATED
                || this == CLOSED_RESOLVED
                || this == CLOSED_WITHDRAWN
                || this == CLOSED_EXPIRED;
    }

    /**
     * Determines whether this status is an active (non-terminal) state.
     *
     * @return {@code true} if the dispute is still in progress
     */
    public boolean isActive() {
        return !isTerminal();
    }

    /**
     * Determines whether withdrawal is permitted from this state.
     * Claimants may withdraw before a ruling has been issued.
     *
     * @return {@code true} if withdrawal is allowed
     */
    public boolean allowsWithdrawal() {
        return this == OPEN
                || this == UNDER_INVESTIGATION
                || this == AWAITING_EVIDENCE
                || this == MEDIATION_IN_PROGRESS
                || this == PENDING_ARBITRATION;
    }
}
