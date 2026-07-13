package com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(
        UUID id, UUID tenantId, UUID agencyId, UUID delivererId,
        String contractType, LocalDate startDate, LocalDate endDate,
        String remunerationModel, BigDecimal baseSalary, BigDecimal commissionRate,
        String status, Instant signedAt) {}
