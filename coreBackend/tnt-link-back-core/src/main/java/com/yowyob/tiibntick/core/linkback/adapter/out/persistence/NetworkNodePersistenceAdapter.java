package com.yowyob.tiibntick.core.linkback.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.NetworkNodeEntity;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository.NetworkNodeR2dbcRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.NetworkNodeRepository;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import com.yowyob.tiibntick.core.linkback.domain.model.NodeRefType;
import com.yowyob.tiibntick.core.linkback.domain.model.NodeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NetworkNodePersistenceAdapter implements NetworkNodeRepository {

    private final NetworkNodeR2dbcRepository r2dbcRepository;

    @Override
    public Mono<NetworkNode> save(NetworkNode node) {
        NetworkNodeEntity entity = toEntity(node);
        return r2dbcRepository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<NetworkNode> findById(UUID tenantId, UUID nodeId) {
        return r2dbcRepository.findByIdAndTenantId(nodeId, tenantId).map(this::toDomain);
    }

    @Override
    public Mono<NetworkNode> findByRefId(UUID tenantId, UUID refId) {
        return r2dbcRepository.findByTenantIdAndRefId(tenantId, refId).map(this::toDomain);
    }

    @Override
    public Flux<NetworkNode> findWithinBoundingBox(UUID tenantId, double minLat, double minLng, double maxLat, double maxLng) {
        return r2dbcRepository.findWithinBoundingBox(tenantId, minLat, maxLat, minLng, maxLng).map(this::toDomain);
    }

    @Override
    public Flux<NetworkNode> findTopRanked(UUID tenantId, int limit) {
        return r2dbcRepository.findTopRanked(tenantId, limit).map(this::toDomain);
    }

    private NetworkNodeEntity toEntity(NetworkNode node) {
        return NetworkNodeEntity.builder()
                .id(node.getId())
                .tenantId(node.getTenantId())
                .refType(node.getRefType().name())
                .refId(node.getRefId())
                .status(node.getStatus().name())
                .trustScore(node.getTrustScore())
                .gamificationLevel(node.getGamificationLevel())
                .communityScore(node.getCommunityScore())
                .latitude(node.getLastKnownLocation() != null ? node.getLastKnownLocation().latitude() : null)
                .longitude(node.getLastKnownLocation() != null ? node.getLastKnownLocation().longitude() : null)
                .heading(node.getHeading())
                .description(node.getDescription())
                .declaredZoneName(node.getDeclaredZoneName())
                .declaredCity(node.getDeclaredCity())
                .declaredCapacityParcels(node.getDeclaredCapacityParcels())
                .badges(String.join(",", node.getBadges()))
                .lastZoneId(node.getLastZoneId())
                .zoneTransitionCount(node.getZoneTransitionCount())
                .polVerified(node.isPolVerified())
                .polPeerCount(node.getPolPeerCount())
                .polVerifiedAt(node.getPolVerifiedAt())
                .didIdentifier(node.getDidIdentifier())
                .didIssuer(node.getDidIssuer())
                .didVerifiedAt(node.getDidVerifiedAt())
                .beaconActive(node.isBeaconActive())
                .beaconMessage(node.getBeaconMessage())
                .beaconExpiresAt(node.getBeaconExpiresAt())
                .beaconRadiusKm(node.getBeaconRadiusKm())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

    private NetworkNode toDomain(NetworkNodeEntity entity) {
        GeoPoint location = entity.getLatitude() != null && entity.getLongitude() != null
                ? GeoPoint.of(entity.getLatitude(), entity.getLongitude())
                : null;
        Set<String> badges = entity.getBadges() == null || entity.getBadges().isBlank()
                ? new HashSet<>()
                : Arrays.stream(entity.getBadges().split(",")).collect(Collectors.toSet());
        return NetworkNode.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .refType(NodeRefType.valueOf(entity.getRefType()))
                .refId(entity.getRefId())
                .status(NodeStatus.valueOf(entity.getStatus()))
                .trustScore(entity.getTrustScore())
                .gamificationLevel(entity.getGamificationLevel())
                .communityScore(entity.getCommunityScore())
                .lastKnownLocation(location)
                .heading(entity.getHeading())
                .description(entity.getDescription())
                .declaredZoneName(entity.getDeclaredZoneName())
                .declaredCity(entity.getDeclaredCity())
                .declaredCapacityParcels(entity.getDeclaredCapacityParcels())
                .badges(badges)
                .lastZoneId(entity.getLastZoneId())
                .zoneTransitionCount(entity.getZoneTransitionCount())
                .polVerified(entity.isPolVerified())
                .polPeerCount(entity.getPolPeerCount())
                .polVerifiedAt(entity.getPolVerifiedAt())
                .didIdentifier(entity.getDidIdentifier())
                .didIssuer(entity.getDidIssuer())
                .didVerifiedAt(entity.getDidVerifiedAt())
                .beaconActive(entity.isBeaconActive())
                .beaconMessage(entity.getBeaconMessage())
                .beaconExpiresAt(entity.getBeaconExpiresAt())
                .beaconRadiusKm(entity.getBeaconRadiusKm())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
