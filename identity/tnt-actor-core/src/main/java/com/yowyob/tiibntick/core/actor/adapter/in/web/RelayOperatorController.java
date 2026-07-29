package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.AvailabilitySlotDto;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.CreateRelayOperatorRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.RelayOperatorProfileResponse;
import com.yowyob.tiibntick.core.actor.application.command.CreateRelayOperatorProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateRelayOperatorProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindRelayOperatorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.exception.RelayOperatorNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.AvailabilitySlot;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.yowyob.tiibntick.common.api.ApiResponse;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST controller for relay operator profile operations.
 *
 * <p>Hub assignment is managerial, not self-service — mirrors
 * {@code DelivererController}'s security model rather than
 * {@code FreelancerController}'s.
 *
 * <p>{@code identity} on the response is resolved live from the Kernel via
 * {@link IResolveActorIdentityUseCase} — never stored on
 * {@code RelayOperatorProfile} itself, so name/phone/email never get
 * duplicated locally.
 *
 * @author MANFOUO Braun
 */
@RestController
@Validated
@RequestMapping("/api/v1/relay-operators")
@Tag(name = "Relay Operators", description = "Relay operator profile management")
public class RelayOperatorController {

    private final ICreateRelayOperatorProfileUseCase createUseCase;
    private final IFindRelayOperatorUseCase findUseCase;
    private final IResolveActorIdentityUseCase identityUseCase;

    public RelayOperatorController(ICreateRelayOperatorProfileUseCase createUseCase,
                                    IFindRelayOperatorUseCase findUseCase,
                                    IResolveActorIdentityUseCase identityUseCase) {
        this.createUseCase = createUseCase;
        this.findUseCase = findUseCase;
        this.identityUseCase = identityUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','TNT_ADMIN')")
    @Operation(summary = "Create a relay operator profile for a user within the caller's tenant")
    public Mono<ResponseEntity<ApiResponse<RelayOperatorProfileResponse>>> createRelayOperatorProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<CreateRelayOperatorRequest> requestMono) {
        return requestMono.flatMap(req -> {
            List<AvailabilitySlot> slots = req.openingHours() != null
                    ? req.openingHours().stream().map(AvailabilitySlotDto::toDomain).toList()
                    : List.of();
            return createUseCase.createRelayOperatorProfile(new CreateRelayOperatorProfileCommand(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    req.hubId(),
                    slots,
                    req.declaredCapacityParcels()));
        })
        .flatMap(profile -> identityUseCase.resolve(profile.actorId())
                .map(identity -> RelayOperatorProfileResponse.from(profile, identity))
                .defaultIfEmpty(RelayOperatorProfileResponse.from(profile)))
        .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('RELAY_OPERATOR')")
    @Operation(summary = "Get the relay operator profile of the authenticated relay operator")
    public Mono<ResponseEntity<ApiResponse<RelayOperatorProfileResponse>>> getMyProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return findUseCase.findByActorId(currentUser.tenantId(), currentUser.userId())
                .switchIfEmpty(Mono.error(
                        new RelayOperatorNotFoundException(currentUser.tenantId(), currentUser.userId())))
                .flatMap(profile -> identityUseCase.resolve(profile.actorId())
                        .map(identity -> RelayOperatorProfileResponse.from(profile, identity))
                        .defaultIfEmpty(RelayOperatorProfileResponse.from(profile)))
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }
}
