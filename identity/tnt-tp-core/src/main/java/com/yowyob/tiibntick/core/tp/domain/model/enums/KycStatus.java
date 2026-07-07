package com.yowyob.tiibntick.core.tp.domain.model.enums;

/**
 * Status of a simplified KYC (Know Your Customer) verification for TiiBnTick actors.
 *
 * @author MANFOUO Braun
 */
public enum KycStatus {

    /** KYC not yet initiated. */
    NOT_SUBMITTED,

    /** Documents submitted, pending review. */
    PENDING_REVIEW,

    /** KYC approved — identity verified. */
    APPROVED,

    /** KYC rejected — reasons provided in KycRecord. */
    REJECTED,

    /** Expired — re-verification required. */
    EXPIRED
}
