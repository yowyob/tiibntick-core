package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of a compensation paid by the Kernel compensation fund for a dispute (spec §4.3, lot L5): one row per dispute,
 * so that a second payment request for the same dispute returns this one without calling the Kernel again.
 */
public record WalletCompensation(UUID id, UUID tenantId, String disputeId, UUID kernelCompensationId,
                                 UUID fundWalletId, UUID beneficiaryActorId, UUID beneficiaryWalletId,
                                 BigDecimal amount, String currency, Instant createdAt) {

    /** Mirror key (and Kernel reference) of a return paid by the fund: {@code RETURN:<returnDeliveryId>}. */
    public static final String RETURN_PREFIX = "RETURN:";

    /**
     * Mirror key (and Kernel reference) of a replacement courier paid by the fund (QO-11 / D-153):
     * {@code REPLACEMENT:<deliveryId>:<index>}.
     */
    public static final String REPLACEMENT_PREFIX = "REPLACEMENT:";

    /** Reference sent to the Kernel: {@code DISPUTE:<disputeId>}. */
    public static String kernelReference(String disputeId) {
        return "DISPUTE:" + disputeId;
    }
}
