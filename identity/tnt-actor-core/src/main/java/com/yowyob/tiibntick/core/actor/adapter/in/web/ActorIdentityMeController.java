package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.UpdateIdentityRequest;
import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.domain.model.UpdateActorIdentityCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Lets the authenticated user read/persist their own personal identity (first name, last name,
 * phone). Identity lives in the Kernel actor; this controller relays the caller's bearer token
 * to {@code GET /api/actors/me} and {@code PUT /api/actors/me/identity}.
 */
@RestController
@Validated
@RequestMapping("/api/v1/actors/me/identity")
@Tag(name = "Actor identity", description = "Personal identity of the authenticated user (stored in the Kernel)")
public class ActorIdentityMeController {

    private final IResolveActorIdentityUseCase resolveUseCase;
    private final IUpdateActorIdentityUseCase updateUseCase;

    public ActorIdentityMeController(IResolveActorIdentityUseCase resolveUseCase,
                                     IUpdateActorIdentityUseCase updateUseCase) {
        this.resolveUseCase = resolveUseCase;
        this.updateUseCase = updateUseCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my identity (best effort: the Kernel exposes name/phone of my business actor)")
    public Mono<ResponseEntity<ApiResponse<ActorIdentitySummary>>> getMyIdentity() {
        return resolveUseCase.resolveCurrent()
                .map(identity -> ResponseEntity.ok(ApiResponse.success(identity)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update my first name, last name and phone number")
    public Mono<ResponseEntity<ApiResponse<ActorIdentitySummary>>> updateMyIdentity(
            @Valid @RequestBody UpdateIdentityRequest request) {
        return updateUseCase.updateCurrent(new UpdateActorIdentityCommand(
                        request.firstName(), request.lastName(), request.phoneNumber()))
                .map(identity -> ResponseEntity.ok(ApiResponse.success(identity)));
    }
}
