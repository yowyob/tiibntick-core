package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.CreateDaoZoneRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.ProposeDaoProposalRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.request.VoteOnProposalRequest;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.DaoProposalResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.DaoProposalResponseMapper;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.DaoZoneResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.DaoZoneResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.ArchiveDaoZoneUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.CloseDaoProposalUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.CreateDaoZoneUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.ProposeDaoProposalUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryDaoProposalsUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryDaoZonesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.VoteOnDaoProposalUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.CreateDaoZoneCommand;
import com.yowyob.tiibntick.core.linkback.application.port.in.command.ProposeDaoProposalCommand;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Generic Link business API for DAO governance zones and their proposals —
 * genuinely new Link domain logic, the single entry point the BFF calls.
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link DAO Zones", description = "Community-governance zones and proposals for the TiiBnTick Link network")
@RestController
@RequestMapping("/api/v1/platform/link/dao")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DaoZoneController {

    private final CreateDaoZoneUseCase createZoneUseCase;
    private final ArchiveDaoZoneUseCase archiveZoneUseCase;
    private final QueryDaoZonesUseCase queryZonesUseCase;
    private final ProposeDaoProposalUseCase proposeUseCase;
    private final VoteOnDaoProposalUseCase voteUseCase;
    private final CloseDaoProposalUseCase closeUseCase;
    private final QueryDaoProposalsUseCase queryProposalsUseCase;

    // ── Zones ──────────────────────────────────────────────────────────

    @Operation(summary = "Create a new DAO governance zone")
    @PostMapping("/zones")
    @PreAuthorize("isAuthenticated()")
    public Mono<DaoZoneResponse> createZone(
            @Valid @RequestBody CreateDaoZoneRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        var command = new CreateDaoZoneCommand(
                currentUser.tenantId(), request.name(), request.description(),
                GeoPoint.of(request.centerLatitude(), request.centerLongitude()),
                request.radiusKm(), resolveActorId(currentUser));
        return createZoneUseCase.create(command).map(DaoZoneResponseMapper::toResponse);
    }

    @Operation(summary = "Archive a DAO zone")
    @DeleteMapping("/zones/{zoneId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<DaoZoneResponse> archiveZone(
            @PathVariable UUID zoneId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return archiveZoneUseCase.archive(currentUser.tenantId(), zoneId).map(DaoZoneResponseMapper::toResponse);
    }

    @Operation(summary = "Get a DAO zone by id")
    @GetMapping("/zones/{zoneId}")
    public Mono<DaoZoneResponse> getZone(
            @PathVariable UUID zoneId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryZonesUseCase.findById(currentUser.tenantId(), zoneId).map(DaoZoneResponseMapper::toResponse);
    }

    @Operation(summary = "List active DAO zones")
    @GetMapping("/zones")
    public Flux<DaoZoneResponse> listActiveZones(@Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryZonesUseCase.findActiveByTenant(currentUser.tenantId()).map(DaoZoneResponseMapper::toResponse);
    }

    @Operation(summary = "Find active DAO zones containing a location")
    @GetMapping("/zones/containing")
    public Flux<DaoZoneResponse> zonesContaining(
            double lat, double lng,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryZonesUseCase.findContaining(currentUser.tenantId(), GeoPoint.of(lat, lng))
                .map(DaoZoneResponseMapper::toResponse);
    }

    // ── Proposals ──────────────────────────────────────────────────────

    @Operation(summary = "Propose a governance change in a DAO zone")
    @PostMapping("/zones/{zoneId}/proposals")
    @PreAuthorize("isAuthenticated()")
    public Mono<DaoProposalResponse> propose(
            @PathVariable UUID zoneId,
            @Valid @RequestBody ProposeDaoProposalRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        var command = new ProposeDaoProposalCommand(
                zoneId, currentUser.tenantId(), request.title(), request.description(),
                resolveActorId(currentUser), request.votingDeadline());
        return proposeUseCase.propose(command).map(DaoProposalResponseMapper::toResponse);
    }

    @Operation(summary = "Vote on an open proposal (one vote per member)")
    @PostMapping("/proposals/{proposalId}/vote")
    @PreAuthorize("isAuthenticated()")
    public Mono<DaoProposalResponse> vote(
            @PathVariable UUID proposalId,
            @Valid @RequestBody VoteOnProposalRequest request,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return voteUseCase.vote(currentUser.tenantId(), proposalId, resolveActorId(currentUser), request.inFavor())
                .map(DaoProposalResponseMapper::toResponse);
    }

    @Operation(summary = "Close a proposal, resolving it as APPROVED or REJECTED by vote tally")
    @PostMapping("/proposals/{proposalId}/close")
    @PreAuthorize("isAuthenticated()")
    public Mono<DaoProposalResponse> close(
            @PathVariable UUID proposalId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return closeUseCase.close(currentUser.tenantId(), proposalId).map(DaoProposalResponseMapper::toResponse);
    }

    @Operation(summary = "Get a proposal by id")
    @GetMapping("/proposals/{proposalId}")
    public Mono<DaoProposalResponse> getProposal(
            @PathVariable UUID proposalId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryProposalsUseCase.findById(currentUser.tenantId(), proposalId)
                .map(DaoProposalResponseMapper::toResponse);
    }

    @Operation(summary = "List all proposals for a zone")
    @GetMapping("/zones/{zoneId}/proposals")
    public Flux<DaoProposalResponse> listProposals(
            @PathVariable UUID zoneId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryProposalsUseCase.findByZone(currentUser.tenantId(), zoneId)
                .map(DaoProposalResponseMapper::toResponse);
    }

    @Operation(summary = "List open (votable) proposals for a zone")
    @GetMapping("/zones/{zoneId}/proposals/open")
    public Flux<DaoProposalResponse> listOpenProposals(
            @PathVariable UUID zoneId,
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryProposalsUseCase.findOpenByZone(currentUser.tenantId(), zoneId)
                .map(DaoProposalResponseMapper::toResponse);
    }

    private UUID resolveActorId(TntUserIdentity currentUser) {
        return currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId();
    }
}
