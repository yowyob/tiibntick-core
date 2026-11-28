package com.yowyob.tiibntick.core.dispute.domain.exception;

/** The compensation could not be paid (spec §4.3); the dispute stays PENDING_COMPENSATION. */
public class DisputeCompensationException extends RuntimeException {

    public static final String UNAVAILABLE = "COMPENSATION_UNAVAILABLE";
    public static final String FUND_INSUFFICIENT = "COMPENSATION_FUND_INSUFFICIENT";

    private final String code;
    private final int httpStatus;

    public DisputeCompensationException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static DisputeCompensationException unavailable() {
        return new DisputeCompensationException(UNAVAILABLE, 503,
                "Compensation payment is not available yet (Kernel compensation fund not wired)");
    }

    public static DisputeCompensationException fundInsufficient(String detail) {
        return new DisputeCompensationException(FUND_INSUFFICIENT, 409, detail);
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
