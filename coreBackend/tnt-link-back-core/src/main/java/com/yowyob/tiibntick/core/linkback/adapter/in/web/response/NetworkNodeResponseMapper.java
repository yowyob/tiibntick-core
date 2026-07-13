package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;

import java.util.List;

public final class NetworkNodeResponseMapper {

    private NetworkNodeResponseMapper() {
    }

    public static NetworkNodeResponse toResponse(NetworkNode node) {
        return new NetworkNodeResponse(
                node.getId(),
                node.getTenantId(),
                node.getRefType().name(),
                node.getRefId(),
                node.getStatus().name(),
                node.getTrustScore(),
                node.getGamificationLevel(),
                node.getCommunityScore(),
                node.getLastKnownLocation() != null ? node.getLastKnownLocation().latitude() : null,
                node.getLastKnownLocation() != null ? node.getLastKnownLocation().longitude() : null,
                node.getHeading(),
                node.getDescription(),
                node.getDeclaredZoneName(),
                node.getDeclaredCity(),
                node.getDeclaredCapacityParcels(),
                List.copyOf(node.getBadges()),
                node.getZoneTransitionCount(),
                node.isPolVerified(),
                node.getPolPeerCount(),
                node.getPolVerifiedAt(),
                node.getDidIdentifier(),
                node.getDidIssuer(),
                node.getDidVerifiedAt(),
                node.isBeaconCurrentlyActive(),
                node.getBeaconMessage(),
                node.getBeaconExpiresAt(),
                node.getBeaconRadiusKm(),
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }
}
