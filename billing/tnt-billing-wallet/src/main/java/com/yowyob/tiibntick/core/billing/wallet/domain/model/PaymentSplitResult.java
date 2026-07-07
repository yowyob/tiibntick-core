package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PaymentSplitResult — value object returned by {@code IWalletUseCase.splitMissionRevenue()}.
 *
 * <p>Summarizes the distribution of funds resulting from a split operation.
 *
 * @author MANFOUO Braun
 */
public record PaymentSplitResult(
        UUID splitId,
        String missionId,
        BigDecimal totalAmount,
        String currency,
        BigDecimal platformCommission,
        BigDecimal orgRevenue,
        BigDecimal subDelivererCommission,
        String subDelivererId,
        String status
) {
    public static PaymentSplitResult fromSplit(PaymentSplit split) {
        return new PaymentSplitResult(
                split.getId(),
                split.getMissionId(),
                split.getTotalAmount(),
                split.getCurrency(),
                split.getPlatformCommission(),
                split.getOrgRevenue(),
                split.getSubDelivererCommission(),
                split.getSubDelivererId(),
                split.getStatus().name());
    }
}
