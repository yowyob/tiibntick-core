package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.application.port.in.ActivateBeaconUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.AwardNodeReputationUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.GetLeaderboardUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryDaoZonesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryNetworkNodesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.RegisterNetworkNodeUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.UpdateNodeLocationUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.UpdateNodeStatusUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.RegisterNetworkNodeCommand;
import com.yowyob.tiibntick.core.linkback.application.port.out.ILinkTileEventPublisher;
import com.yowyob.tiibntick.core.linkback.application.port.out.NetworkNodeRepository;
import com.yowyob.tiibntick.common.util.TntGeohashUtil;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkNodeDomainException;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import com.yowyob.tiibntick.core.linkback.domain.model.NodeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Genuinely new Link business logic — orchestrates Link's own node registry,
 * a thin extension layer over existing tnt-actor-core/tnt-organization-core
 * entities (see {@link NetworkNode} javadoc).
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class NetworkNodeApplicationService implements
        RegisterNetworkNodeUseCase, UpdateNodeStatusUseCase,
        UpdateNodeLocationUseCase, QueryNetworkNodesUseCase,
        GetLeaderboardUseCase, AwardNodeReputationUseCase,
        ActivateBeaconUseCase {

    private final NetworkNodeRepository repository;
    private final QueryDaoZonesUseCase queryDaoZonesUseCase;
    private final ILinkTileEventPublisher tilePublisher;

    @Override
    public Mono<NetworkNode> register(RegisterNetworkNodeCommand command) {
        NetworkNode node = NetworkNode.register(command.tenantId(), command.refType(), command.refId(),
                command.description(), command.declaredZoneName(), command.declaredCity(),
                command.declaredCapacityParcels());
        return repository.save(node);
    }

    @Override
    public Mono<NetworkNode> updateStatus(UUID tenantId, UUID nodeId, NodeStatus status) {
        return findOrError(tenantId, nodeId)
                .flatMap(node -> {
                    node.updateStatus(status);
                    return repository.save(node);
                });
    }

    @Override
    public Mono<NetworkNode> updateLocation(UUID tenantId, UUID nodeId, GeoPoint location, Double heading, int polPeerCount) {
        return findOrError(tenantId, nodeId)
                .flatMap(node -> {
                    GeoPoint previousLocation = node.getLastKnownLocation();
                    node.updateLocation(location, heading);
                    node.recordProofOfLocation(polPeerCount);
                    // .next() + .map() yields an empty Mono when no zone contains the point —
                    // doOnNext simply doesn't fire in that case, .then(...) still proceeds.
                    return queryDaoZonesUseCase.findContaining(tenantId, location)
                            .next()
                            .map(zone -> zone.getId())
                            .doOnNext(node::recordZoneTransition)
                            .then(Mono.defer(() -> {
                                node.refreshBadges();
                                return repository.save(node);
                            }))
                            .flatMap(saved -> publishIfTileChanged(saved, previousLocation).thenReturn(saved));
                });
    }

    /**
     * Chantier G tile fan-out: only publish when the node actually entered a different
     * geohash tile (or this is its first fix) — an in-tile GPS jitter doesn't need to
     * reach subscribers, keeping fan-out volume proportional to real movement (Audit n6
     * S23 / n7 P1 bis: "n'émettre que les entités entrées/sorties/déplacées de la tuile").
     */
    private Mono<Void> publishIfTileChanged(NetworkNode saved, GeoPoint previousLocation) {
        GeoPoint current = saved.getLastKnownLocation();
        if (current == null) {
            return Mono.empty();
        }
        String newTile = TntGeohashUtil.encode(current.latitude(), current.longitude());
        boolean tileChanged = previousLocation == null
                || !newTile.equals(TntGeohashUtil.encode(previousLocation.latitude(), previousLocation.longitude()));
        return tileChanged ? tilePublisher.publishNodeMoved(saved) : Mono.empty();
    }

    @Override
    public Mono<NetworkNode> findById(UUID tenantId, UUID nodeId) {
        return repository.findById(tenantId, nodeId);
    }

    @Override
    public Mono<NetworkNode> findByRefId(UUID tenantId, UUID refId) {
        return repository.findByRefId(tenantId, refId);
    }

    @Override
    public Flux<NetworkNode> findByRefIds(UUID tenantId, java.util.Collection<UUID> refIds) {
        return repository.findByRefIds(tenantId, refIds);
    }

    @Override
    public Flux<NetworkNode> findWithinBoundingBox(UUID tenantId, double minLat, double minLng, double maxLat, double maxLng) {
        return repository.findWithinBoundingBox(tenantId, minLat, minLng, maxLat, maxLng);
    }

    @Override
    public Flux<NetworkNode> getTopNodes(UUID tenantId, int limit) {
        return repository.findTopRanked(tenantId, limit);
    }

    @Override
    public Mono<Void> awardTrust(UUID tenantId, UUID refId, double trustDelta) {
        return repository.findByRefId(tenantId, refId)
                .flatMap(node -> {
                    node.earnTrust(trustDelta);
                    node.refreshBadges();
                    return repository.save(node);
                })
                .then();
    }

    @Override
    public Mono<Void> awardPoints(UUID tenantId, UUID refId, int pointsDelta) {
        return repository.findByRefId(tenantId, refId)
                .flatMap(node -> {
                    node.awardPoints(pointsDelta);
                    node.refreshBadges();
                    return repository.save(node);
                })
                .then();
    }

    @Override
    public Mono<NetworkNode> activate(UUID tenantId, UUID nodeId, String message, double radiusKm, Duration duration) {
        return findOrError(tenantId, nodeId)
                .flatMap(node -> {
                    node.activateBeacon(message, radiusKm, duration);
                    return repository.save(node);
                });
    }

    @Override
    public Mono<NetworkNode> deactivate(UUID tenantId, UUID nodeId) {
        return findOrError(tenantId, nodeId)
                .flatMap(node -> {
                    node.deactivateBeacon();
                    return repository.save(node);
                });
    }

    private Mono<NetworkNode> findOrError(UUID tenantId, UUID nodeId) {
        return repository.findById(tenantId, nodeId)
                .switchIfEmpty(Mono.error(new NetworkNodeDomainException("Network node not found: " + nodeId)));
    }
}
