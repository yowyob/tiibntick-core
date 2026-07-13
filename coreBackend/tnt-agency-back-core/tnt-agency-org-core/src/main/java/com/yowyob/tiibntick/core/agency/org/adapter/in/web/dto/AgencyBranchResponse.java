package com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AgencyBranchResponse(
        UUID id,
        UUID tenantId,
        UUID agencyId,
        String name,
        String code,
        UUID managerId,
        String status,
        UUID coreBranchId,
        AddressResponse address,
        Instant createdAt,
        Instant updatedAt
) {
    public record AddressResponse(
            String street, String landmark, String quarter,
            String city, String region, String country, String postalCode,
            Double lat, Double lon) {}
}
