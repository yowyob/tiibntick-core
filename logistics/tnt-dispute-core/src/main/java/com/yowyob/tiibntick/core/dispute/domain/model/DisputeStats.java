package com.yowyob.tiibntick.core.dispute.domain.model;

import java.math.BigDecimal;

/**
 * Dispute statistics for a given FreelancerOrg or actor, for a period.
 *
 * <p>Used by platform admins and FreelancerOrg owners to monitor their dispute health.
 * High disputeRate or compensationRate can trigger KYC re-review.
 *
 * @author MANFOUO Braun
 */
public record DisputeStats(
        String orgId,
        String periodLabel,
        long totalDisputes,
        long openDisputes,
        long resolvedDisputes,
        long closedWithCompensation,
        long closedWithdrawn,
        BigDecimal totalCompensationXAF,
        double disputeRate,
        double compensationRate
) {
    public static DisputeStats empty(String orgId, String period) {
        return new DisputeStats(orgId, period, 0, 0, 0, 0, 0, BigDecimal.ZERO, 0.0, 0.0);
    }
}
