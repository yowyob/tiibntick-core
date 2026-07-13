package com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record StaffMemberResponse(
        UUID id,
        UUID agencyId,
        UUID branchId,
        String fullName,
        String phone,
        String email,
        String role,
        String status,
        Instant joinedAt) {}
