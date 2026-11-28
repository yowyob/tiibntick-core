package com.yowyob.tiibntick.core.billing.wallet.domain.exception;

/**
 * A Kernel money operation (escrow, cash collection, compensation fund) could not be done (lot L5, spec §4). Carries a
 * stable code and the HTTP status the caller should answer.
 */
public class WalletSettlementException extends RuntimeException {

    public static final String FUND_INSUFFICIENT = "COMPENSATION_FUND_INSUFFICIENT";
    public static final String BENEFICIARY_WALLET_MISSING = "BENEFICIARY_WALLET_MISSING";
    public static final String KERNEL_UNAVAILABLE = "KERNEL_PAYMENT_UNAVAILABLE";
    public static final String KERNEL_REFUSED = "KERNEL_PAYMENT_REFUSED";
    public static final String PLATFORM_WALLET_MISSING = "PLATFORM_WALLET_NOT_CONFIGURED";
    public static final String INVALID_SPLIT = "PAYMENT_SPLIT_INVALID";
    public static final String WALLET_MISSING = "WALLET_MISSING";
    public static final String PAYMENT_INTENT_NOT_FOUND = "PAYMENT_INTENT_NOT_FOUND";
    public static final String PAYMENT_INTENT_FORBIDDEN = "PAYMENT_INTENT_FORBIDDEN";
    public static final String PAYMENT_INTENT_INVALID = "PAYMENT_INTENT_INVALID";
    public static final String PAYMENT_INTENT_EXPIRED = "PAYMENT_INTENT_EXPIRED";
    public static final String CASH_COLLECTION_FORBIDDEN = "CASH_COLLECTION_FORBIDDEN";
    public static final String CASH_COLLECTION_NOT_FOUND = "CASH_COLLECTION_NOT_FOUND";
    public static final String DEBT_NOT_FOUND = "DEBT_NOT_FOUND";
    public static final String DEBT_FORBIDDEN = "DEBT_FORBIDDEN";
    public static final String DEBT_NOT_RECOVERABLE = "DEBT_NOT_RECOVERABLE";

    private final String code;
    private final int httpStatus;

    public WalletSettlementException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public WalletSettlementException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
