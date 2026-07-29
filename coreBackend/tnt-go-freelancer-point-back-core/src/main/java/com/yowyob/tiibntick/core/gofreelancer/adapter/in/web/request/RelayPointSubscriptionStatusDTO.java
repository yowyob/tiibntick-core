package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayPointSubscriptionStatusDTO {
    private UUID subscriptionId;
    private UUID relayPointId;
    // Plan
    private String plan;
    private String status;
    private Float price;
    private String paymentMethod;
    // Quota
    private int maxDeposits;
    private boolean unlimited;
    private int depositsUsed;
    private int depositsRemaining;
    private int quotaUsagePercent;
    private Instant resetDate;
    // Commission
    private double commissionPercent;
    private double netPercent;
    // Validity
    private Instant startDate;
    private Instant endDate;
    // Eligibility shortcut
    private boolean eligible;
}
