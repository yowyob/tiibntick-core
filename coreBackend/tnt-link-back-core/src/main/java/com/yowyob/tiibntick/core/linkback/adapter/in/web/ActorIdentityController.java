package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.ActorIdentityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Thin, reusable pass-through over tnt-actor-core's
 * {@code IResolveActorIdentityUseCase} — a single generic entry point the
 * Link BFF (or any future consumer) calls to resolve a display name/phone/
 * email for an actor id, instead of every Link feature (alerts, board,
 * leaderboard...) inventing its own identity-resolution logic.
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Actor Identity", description = "Resolves a display identity for an actor id")
@RestController
@RequestMapping("/api/v1/platform/link/actors")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ActorIdentityController {

    private final IResolveActorIdentityUseCase resolveActorIdentityUseCase;

    @Operation(summary = "Resolve an actor's display identity (name, phone, email)")
    @GetMapping("/{actorId}/identity")
    public Mono<ActorIdentityResponse> getIdentity(@PathVariable UUID actorId) {
        return resolveActorIdentityUseCase.resolve(actorId)
                .map(summary -> new ActorIdentityResponse(
                        summary.actorId(), summary.displayName(), summary.phoneNumber(), summary.email()));
    }

    @Operation(summary = "Resolve display identities for a batch of actor ids in one round trip — "
            + "for callers (e.g. the Link BFF's board screen) that would otherwise need one call per actor")
    @PostMapping("/identities/batch")
    @PreAuthorize("isAuthenticated()")
    public Flux<ActorIdentityResponse> getIdentities(@RequestBody List<UUID> actorIds) {
        return Flux.fromIterable(actorIds)
                .flatMap(actorId -> resolveActorIdentityUseCase.resolve(actorId)
                        .map(summary -> new ActorIdentityResponse(
                                summary.actorId(), summary.displayName(), summary.phoneNumber(), summary.email()))
                        .defaultIfEmpty(new ActorIdentityResponse(actorId, null, null, null)));
    }
}
