package com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DelivererResponse(
        UUID id, UUID tenantId, UUID agencyId, UUID branchId, UUID actorId,
        String phone, String status, Instant joinedAt, Instant suspendedAt) {}
