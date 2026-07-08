package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.CreateDelivererRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.DelivererProfileResponse;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.RateActorRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.UpdateActorLocationRequest;
import com.yowyob.tiibntick.core.actor.application.command.AssignMissionCommand;
import com.yowyob.tiibntick.core.actor.application.command.CreateDelivererProfileCommand;
import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.command.ReleaseMissionCommand;
import com.yowyob.tiibntick.core.actor.application.command.UpdateActorLocationCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IAssignMissionToDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateDelivererProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IGetAvailableDeliverersNearUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IReleaseMissionFromDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorLocationUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.yowyob.tiibntick.common.api.ApiResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for deliverer profile operations.
 *
 * <p> — Migrated from {@code ReactiveRequestContextHolder} (Kernel pattern) to
 * {@code @CurrentUser TntUserIdentity} (tnt-auth-core pattern). This makes controllers
 * testable without the Kernel context and aligns with the TiiBnTick security model.
 *
 * <p>The {@link TntUserIdentity} parameter is injected by
 * {@code TntCurrentUserArgumentResolver} (registered by tnt-auth-core auto-configuration).
 * It carries {@code userId()}, {@code tenantId()}, {@code actorId()}, {@code roles()}
 * and {@code permissions()} extracted from the validated JWT.
 *
 * @author MANFOUO Braun
 */
@RestController
@Validated
@RequestMapping("/api/v1/deliverers")
@Tag(name = "Deliverers", description = "Permanent deliverer profile management")
public class DelivererController {

    private final ICreateDelivererProfileUseCase createUseCase;
    private final IFindDelivererUseCase findUseCase;
    private final IAssignMissionToDelivererUseCase assignMissionUseCase;
    private final IReleaseMissionFromDelivererUseCase releaseMissionUseCase;
    private final IUpdateActorLocationUseCase updateLocationUseCase;
    private final IRateActorUseCase rateActorUseCase;
    private final IGetAvailableDeliverersNearUseCase availableNearUseCase;

    public DelivererController(ICreateDelivererProfileUseCase createUseCase,
                                IFindDelivererUseCase findUseCase,
                                IAssignMissionToDelivererUseCase assignMissionUseCase,
                                IReleaseMissionFromDelivererUseCase releaseMissionUseCase,
                                IUpdateActorLocationUseCase updateLocationUseCase,
                                IRateActorUseCase rateActorUseCase,
                                IGetAvailableDeliverersNearUseCase availableNearUseCase) {
        this.createUseCase = createUseCase;
        this.findUseCase = findUseCase;
        this.assignMissionUseCase = assignMissionUseCase;
        this.releaseMissionUseCase = releaseMissionUseCase;
        this.updateLocationUseCase = updateLocationUseCase;
        this.rateActorUseCase = rateActorUseCase;
        this.availableNearUseCase = availableNearUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    @Operation(summary = "Create a deliverer profile for a user within the caller's tenant")
    public Mono<ResponseEntity<ApiResponse<DelivererProfileResponse>>> createDelivererProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<CreateDelivererRequest> requestMono) {
        return requestMono.flatMap(req ->
                createUseCase.createDelivererProfile(new CreateDelivererProfileCommand(
                        currentUser.tenantId(),
                        currentUser.userId(),
                        req.agencyId(),
                        req.branchId(),
                        req.capacityKg(),
                        req.delivererType(),
                        req.contractId())))
                .map(DelivererProfileResponse::from)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(r)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PERMANENT_DELIVERER')")
    @Operation(summary = "Get the deliverer profile of the authenticated deliverer")
    public Mono<ResponseEntity<ApiResponse<DelivererProfileResponse>>> getMyProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return findUseCase.findByActorId(currentUser.tenantId(), currentUser.userId())
                .map(DelivererProfileResponse::from)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/agency/{agencyId}")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','TNT_ADMIN')")
    @Operation(summary = "List all deliverers in a given agency")
    public Flux<DelivererProfileResponse> getByAgency(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID agencyId) {
        return findUseCase.findByAgency(currentUser.tenantId(), agencyId)
                .map(DelivererProfileResponse::from);
    }

    @GetMapping("/branch/{branchId}/available")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    @Operation(summary = "List available deliverers in a branch (active, no active mission)")
    public Flux<DelivererProfileResponse> getAvailableInBranch(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID branchId) {
        return findUseCase.findAvailableInBranch(currentUser.tenantId(), branchId)
                .map(DelivererProfileResponse::from);
    }

    @GetMapping("/available-near")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Find available deliverers near a GPS coordinate")
    public Flux<DelivererProfileResponse> getAvailableNear(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @RequestParam(defaultValue = "0.0") double minCapacityKg) {
        return availableNearUseCase.findAvailableNear(
                currentUser.tenantId(), latitude, longitude, radiusKm, minCapacityKg)
                .map(DelivererProfileResponse::from);
    }

    @PatchMapping("/me/location")
    @PreAuthorize("hasAnyRole('PERMANENT_DELIVERER','FREELANCER')")
    @Operation(summary = "Update GPS location of the authenticated deliverer")
    public Mono<ResponseEntity<Void>> updateMyLocation(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<UpdateActorLocationRequest> requestMono) {
        return requestMono.flatMap(req ->
                updateLocationUseCase.updateLocation(new UpdateActorLocationCommand(
                        currentUser.tenantId(),
                        currentUser.userId(),
                        ActorType.PERMANENT_DELIVERER,
                        req.latitude(),
                        req.longitude(),
                        req.accuracy(),
                        req.resolvedSource())))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{actorId}/missions/{missionId}/assign")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    @Operation(summary = "Assign a mission to a deliverer")
    public Mono<ResponseEntity<ApiResponse<DelivererProfileResponse>>> assignMission(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID actorId,
            @PathVariable UUID missionId) {
        return assignMissionUseCase.assignMission(
                new AssignMissionCommand(currentUser.tenantId(), actorId, missionId))
                .map(DelivererProfileResponse::from)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PostMapping("/{actorId}/missions/{missionId}/release")
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','PERMANENT_DELIVERER','TNT_ADMIN')")
    @Operation(summary = "Release (unassign) a mission from a deliverer")
    public Mono<ResponseEntity<ApiResponse<DelivererProfileResponse>>> releaseMission(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID actorId,
            @PathVariable UUID missionId) {
        return releaseMissionUseCase.releaseMission(
                new ReleaseMissionCommand(currentUser.tenantId(), actorId, missionId))
                .map(DelivererProfileResponse::from)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PostMapping("/{actorId}/rate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rate a deliverer after a completed delivery")
    public Mono<ResponseEntity<Void>> rateDeliverer(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID actorId,
            @Valid @RequestBody Mono<RateActorRequest> requestMono) {
        return requestMono.flatMap(req ->
                rateActorUseCase.rateActor(new RateActorCommand(
                        currentUser.tenantId(),
                        actorId,
                        ActorType.PERMANENT_DELIVERER,
                        req.score(),
                        req.ratedByActorId())))
                .thenReturn(ResponseEntity.noContent().build());
    }
}
