package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.ActivateBeaconRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.RegisterNetworkNodeRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.UpdateNodeLocationRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.UpdateNodeStatusRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.NetworkNodeProfileResponseMapper;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.NetworkNodeResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.NetworkNodeProfileResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.NetworkNodeResponseMapper;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.ratelimit.NearbyRateLimiter;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.TrustLinkResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.TrustLinkResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.ActivateBeaconUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.EndorseNodeUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.GetNetworkNodeProfileUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryNetworkNodesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryTrustLinksUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.RegisterNetworkNodeUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.UpdateNodeLocationUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.UpdateNodeStatusUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.RegisterNetworkNodeCommand;
import com.yowyob.tiibntick.core.linkback.domain.exception.NearbyRateLimitExceededException;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkNodeDomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Generic Link business API for network nodes — the single entry point the
 * Link BFF calls to register/track drivers, hubs, agencies and relay points.
 * {@code refId} must already exist as an actor/organization in tnt-actor-core
 * or tnt-organization-core; this controller never creates profiles itself
 * (see {@link com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode} javadoc).
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Network Nodes", description = "Drivers, hubs, agencies and relay points on the Link network")
@RestController
@RequestMapping("/api/v1/platform/link/nodes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NetworkNodeController {

    private final RegisterNetworkNodeUseCase registerUseCase;
    private final UpdateNodeStatusUseCase updateStatusUseCase;
    private final UpdateNodeLocationUseCase updateLocationUseCase;
    private final QueryNetworkNodesUseCase queryUseCase;
    private final GetNetworkNodeProfileUseCase profileUseCase;
    private final ActivateBeaconUseCase beaconUseCase;
    private final EndorseNodeUseCase endorseNodeUseCase;
    private final QueryTrustLinksUseCase queryTrustLinksUseCase;
    private final NearbyRateLimiter nearbyRateLimiter;

    @Operation(summary = "Register a Link network node for an existing actor/organization")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<NetworkNodeResponse> register(
            @Valid @RequestBody RegisterNetworkNodeRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        var command = new RegisterNetworkNodeCommand(currentUser.tenantId(), request.refType(), request.refId(),
                request.description(), request.declaredZoneName(), request.declaredCity(),
                request.declaredCapacityParcels());
        return registerUseCase.register(command).map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Get a network node by id")
    @GetMapping("/{nodeId}")
    public Mono<NetworkNodeResponse> getById(
            @PathVariable UUID nodeId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findById(currentUser.tenantId(), nodeId).map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Get a network node's full composed profile (identity, rating, deliveries, zones, endorsements)")
    @GetMapping("/{nodeId}/profile")
    public Mono<NetworkNodeProfileResponse> getProfile(
            @PathVariable UUID nodeId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return profileUseCase.getProfile(currentUser.tenantId(), nodeId).map(NetworkNodeProfileResponseMapper::toResponse);
    }

    @Operation(summary = "Get the network node extending a given actor/organization, if one is registered")
    @GetMapping("/by-ref/{refId}")
    public Mono<NetworkNodeResponse> getByRefId(
            @PathVariable UUID refId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findByRefId(currentUser.tenantId(), refId).map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Get the network nodes extending a batch of actor/organization ids in one round trip")
    @PostMapping("/by-ref/batch")
    @PreAuthorize("isAuthenticated()")
    public Flux<NetworkNodeResponse> getByRefIds(
            @RequestBody List<UUID> refIds,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return Flux.fromIterable(refIds)
                .flatMap(refId -> queryUseCase.findByRefId(currentUser.tenantId(), refId))
                .map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Find network nodes within a bounding box (capped at "
            + "NetworkNodeR2dbcRepository.MAX_NEARBY_RESULTS results, throttled per user — Phase 0 stop-gap)")
    @GetMapping("/nearby")
    public Flux<NetworkNodeResponse> nearby(
            @RequestParam double minLat,
            @RequestParam double minLng,
            @RequestParam double maxLat,
            @RequestParam double maxLng,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return nearbyRateLimiter.tryAcquire(currentUser.tenantId(), currentUser.userId(), "nodes")
                .flatMapMany(allowed -> allowed
                        ? queryUseCase.findWithinBoundingBox(currentUser.tenantId(), minLat, minLng, maxLat, maxLng)
                                .map(NetworkNodeResponseMapper::toResponse)
                        : Flux.error(new NearbyRateLimitExceededException(
                                "Too many /nearby requests — please slow down and try again shortly")));
    }

    @Operation(summary = "Update a node's live status (online/offline/busy)")
    @PutMapping("/{nodeId}/status")
    @PreAuthorize("isAuthenticated()")
    public Mono<NetworkNodeResponse> updateStatus(
            @PathVariable UUID nodeId,
            @Valid @RequestBody UpdateNodeStatusRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return updateStatusUseCase.updateStatus(currentUser.tenantId(), nodeId, request.status())
                .map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Update a node's last known location (heading and Proof-of-Location peer count optional)")
    @PutMapping("/{nodeId}/location")
    @PreAuthorize("isAuthenticated()")
    public Mono<NetworkNodeResponse> updateLocation(
            @PathVariable UUID nodeId,
            @Valid @RequestBody UpdateNodeLocationRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        GeoPoint location = GeoPoint.of(request.latitude(), request.longitude());
        int peerCount = request.polPeerCount() != null ? request.polPeerCount() : 0;
        return updateLocationUseCase.updateLocation(currentUser.tenantId(), nodeId, location, request.heading(), peerCount)
                .map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Activate this node's beacon (broadcasts a message within a radius until it expires)")
    @PostMapping("/{nodeId}/beacon")
    @PreAuthorize("isAuthenticated()")
    public Mono<NetworkNodeResponse> activateBeacon(
            @PathVariable UUID nodeId,
            @Valid @RequestBody ActivateBeaconRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return beaconUseCase.activate(currentUser.tenantId(), nodeId, request.message(),
                        request.radiusKm(), Duration.ofMinutes(request.durationMinutes()))
                .map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Deactivate this node's beacon")
    @DeleteMapping("/{nodeId}/beacon")
    @PreAuthorize("isAuthenticated()")
    public Mono<NetworkNodeResponse> deactivateBeacon(
            @PathVariable UUID nodeId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return beaconUseCase.deactivate(currentUser.tenantId(), nodeId).map(NetworkNodeResponseMapper::toResponse);
    }

    @Operation(summary = "Endorse another node, using the caller's own registered node as the source")
    @PostMapping("/{toNodeId}/endorse")
    @PreAuthorize("isAuthenticated()")
    public Mono<TrustLinkResponse> endorse(
            @PathVariable UUID toNodeId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        UUID actorId = currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
        return queryUseCase.findByRefId(currentUser.tenantId(), actorId)
                .switchIfEmpty(Mono.error(new NetworkNodeDomainException(
                        "You must register a network node before endorsing others")))
                .flatMap(myNode -> endorseNodeUseCase.endorse(currentUser.tenantId(), myNode.getId(), toNodeId))
                .map(TrustLinkResponseMapper::toResponse);
    }

    @Operation(summary = "List endorsements a node has received")
    @GetMapping("/{nodeId}/endorsements")
    public Flux<TrustLinkResponse> endorsements(
            @PathVariable UUID nodeId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryTrustLinksUseCase.findEndorsementsReceivedBy(currentUser.tenantId(), nodeId)
                .map(TrustLinkResponseMapper::toResponse);
    }
}
