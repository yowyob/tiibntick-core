package com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FreelancerAssociationResponse(
        UUID id, UUID tenantId, UUID agencyId, UUID freelancerActorId,
        BigDecimal commissionRate, LocalDate startDate, LocalDate endDate,
        String status, Instant associatedAt) {}
