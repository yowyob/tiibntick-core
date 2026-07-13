package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.ReportNetworkAlertRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.NetworkAlertResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.NetworkAlertResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.ConfirmNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryNetworkAlertsUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ReportNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ResolveNetworkAlertUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.ReportNetworkAlertCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generic Link business API for community-reported network alerts — the single
 * entry point the Link BFF calls; this is genuinely new Link domain logic, not
 * a proxy over an existing L2-L5 module.
 *
 * <p>Endpoints require authentication ({@code @CurrentUser}) but no additional
 * {@code @RequirePermission} gate in this first pass — any authenticated Link
 * actor may report/confirm/resolve a community alert. Finer-grained authorization
 * (e.g. restricting resolve to staff roles) is a deliberate future refinement,
 * not implemented here to avoid touching the shared {@code TntRole} permission
 * vocabulary before it's actually needed.
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Network Alerts", description = "Community-reported traffic alerts for the TiiBnTick Link network")
@RestController
@RequestMapping("/api/v1/platform/link/alerts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NetworkAlertController {

    private final ReportNetworkAlertUseCase reportUseCase;
    private final ConfirmNetworkAlertUseCase confirmUseCase;
    private final ResolveNetworkAlertUseCase resolveUseCase;
    private final QueryNetworkAlertsUseCase queryUseCase;

    @Operation(summary = "Report a new network alert (pothole, flooding, road closure...)")
    @PostMapping
    public Mono<NetworkAlertResponse> report(
            @Valid @RequestBody ReportNetworkAlertRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        ReportNetworkAlertCommand command = new ReportNetworkAlertCommand(
                currentUser.tenantId(),
                resolveActorId(currentUser),
                request.type(),
                request.description(),
                GeoPoint.of(request.latitude(), request.longitude()),
                request.severity());
        return reportUseCase.report(command).map(NetworkAlertResponseMapper::toResponse);
    }

    @Operation(summary = "Confirm (community upvote) an existing alert")
    @PostMapping("/{alertId}/confirm")
    public Mono<NetworkAlertResponse> confirm(
            @PathVariable UUID alertId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return confirmUseCase.confirm(currentUser.tenantId(), alertId)
                .map(NetworkAlertResponseMapper::toResponse);
    }

    @Operation(summary = "Resolve an alert (issue has been fixed / no longer relevant)")
    @PostMapping("/{alertId}/resolve")
    public Mono<NetworkAlertResponse> resolve(
            @PathVariable UUID alertId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return resolveUseCase.resolve(currentUser.tenantId(), alertId)
                .map(NetworkAlertResponseMapper::toResponse);
    }

    @Operation(summary = "Get an alert by id")
    @GetMapping("/{alertId}")
    public Mono<NetworkAlertResponse> getById(
            @PathVariable UUID alertId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findById(currentUser.tenantId(), alertId).map(NetworkAlertResponseMapper::toResponse);
    }

    @Operation(summary = "Find active alerts near a location")
    @GetMapping("/nearby")
    public Flux<NetworkAlertResponse> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radiusKm,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryUseCase.findActiveNearby(currentUser.tenantId(), GeoPoint.of(lat, lng), radiusKm)
                .map(NetworkAlertResponseMapper::toResponse);
    }

    private UUID resolveActorId(TntUserIdentity currentUser) {
        return currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
    }
}
