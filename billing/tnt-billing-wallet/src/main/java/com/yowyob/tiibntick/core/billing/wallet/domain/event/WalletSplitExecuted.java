package com.yowyob.tiibntick.core.billing.wallet.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event — emitted when a mission's revenue has been split between the platform,
 * the FreelancerOrg, and (optionally) a sub-deliverer.
 *
 * <p>Field names/types mirror exactly what {@code BillingEventAccountingConsumer}
 * (tnt-accounting-core) expects on the wire — plain {@link BigDecimal} amounts, not
 * nested {@code Money} objects, since the consumer parses them with
 * {@code new BigDecimal(node.path("orgRevenue").asText("0"))}.
 *
 * @author MANFOUO Braun
 */
public record WalletSplitExecuted(
        String missionId,
        String freelancerOrgId,
        String subDelivererId,
        UUID tenantId,
        BigDecimal totalAmount,
        BigDecimal platformFee,
        BigDecimal orgRevenue,
        BigDecimal subDelivererCommission,
        LocalDateTime occurredAt
) {
    public WalletSplitExecuted(String missionId, String freelancerOrgId, String subDelivererId,
                                UUID tenantId, BigDecimal totalAmount, BigDecimal platformFee,
                                BigDecimal orgRevenue, BigDecimal subDelivererCommission) {
        this(missionId, freelancerOrgId, subDelivererId, tenantId, totalAmount, platformFee,
                orgRevenue, subDelivererCommission, LocalDateTime.now());
    }
}
