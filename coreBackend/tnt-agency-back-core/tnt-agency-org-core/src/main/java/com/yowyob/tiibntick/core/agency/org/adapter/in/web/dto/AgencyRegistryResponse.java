package com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AgencyRegistryResponse(
        UUID id,
        UUID tenantId,
        String name,
        String agencyCode,
        String type,
        String status,
        String registrationNumber,
        AddressResponse address,
        String contactEmail,
        String contactPhone,
        String logoUrl,
        String website,
        UUID kernelOrganizationId,
        UUID kernelBusinessActorId,
        UUID coreAgencyId,
        Instant createdAt,
        Instant updatedAt
) {
    public record AddressResponse(
            String street, String landmark, String quarter,
            String city, String region, String country, String postalCode,
            Double lat, Double lon) {}
}
