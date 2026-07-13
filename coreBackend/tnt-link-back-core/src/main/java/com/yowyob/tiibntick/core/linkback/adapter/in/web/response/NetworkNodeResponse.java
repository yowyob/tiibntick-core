package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NetworkNodeResponse(
        UUID id,
        UUID tenantId,
        String refType,
        UUID refId,
        String status,
        double trustScore,
        int gamificationLevel,
        double communityScore,
        Double latitude,
        Double longitude,
        Double heading,
        String description,
        String declaredZoneName,
        String declaredCity,
        Integer declaredCapacityParcels,
        List<String> badges,
        int zoneTransitionCount,
        boolean polVerified,
        int polPeerCount,
        Instant polVerifiedAt,
        String didIdentifier,
        String didIssuer,
        Instant didVerifiedAt,
        boolean beaconActive,
        String beaconMessage,
        Instant beaconExpiresAt,
        Double beaconRadiusKm,
        Instant createdAt,
        Instant updatedAt
) {
}
