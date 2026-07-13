package com.yowyob.tiibntick.core.agency.commission.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionResponse(
        UUID id,
        UUID tenantId,
        UUID agencyId,
        UUID delivererId,
        UUID missionId,
        BigDecimal amount,
        String currency,
        String status,
        String disputeReason,
        Instant paidAt,
        Instant createdAt) {}
