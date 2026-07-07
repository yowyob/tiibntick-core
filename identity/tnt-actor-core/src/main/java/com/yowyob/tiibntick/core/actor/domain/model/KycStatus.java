package com.yowyob.tiibntick.core.actor.domain.model;

/**
 * KYC (Know Your Customer) verification status for TiiBnTick actor profiles.
 *
 * <p> — Added {@link #FLAGGED} status for actors suspected of fraud following
 * an incident investigation by {@code tnt-incident-core}. A flagged actor cannot
 * operate on the platform until the flag is reviewed and cleared by an administrator.
 *
 * @author MANFOUO Braun
 */
public enum KycStatus {

    /** Initial state — KYC documents not yet submitted. */
    PENDING,

    /** Documents submitted and under review by the KYC team. */
    UNDER_REVIEW,

    /** KYC approved — actor can operate on the platform. */
    VERIFIED,

    /** KYC rejected — actor must resubmit documents. */
    REJECTED,

    /**
     * Actor flagged for suspected fraud by {@code tnt-incident-core}.
     * Set via {@code IActorReputationPort.flagForFraud()} when an incident
     * is escalated to dispute with {@code DisputeType.FRAUD}.
     * A flagged actor is suspended from operations until manually reviewed.
     */
    FLAGGED;

    public static KycStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        return valueOf(value.trim().toUpperCase());
    }

    /** Returns true if the actor has passed KYC verification. */
    public boolean isVerified() {
        return this == VERIFIED;
    }

    /** Returns true if the actor is allowed full access to platform operations. */
    public boolean allowsFullAccess() {
        return this == VERIFIED;
    }

    /** Returns true if the actor is currently suspended due to fraud flag. */
    public boolean isFlagged() {
        return this == FLAGGED;
    }

    /** Returns true if the actor can perform deliveries (verified and not flagged). */
    public boolean isOperational() {
        return this == VERIFIED;
    }
}
