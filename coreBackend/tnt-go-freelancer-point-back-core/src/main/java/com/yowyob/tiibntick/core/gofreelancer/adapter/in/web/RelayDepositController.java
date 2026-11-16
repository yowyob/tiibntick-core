package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayDepositUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Parcels deposited at relay points.
 *
 * <p>Access: the deposits of a relay point are read by its owner or staff; the deposits of a client
 * by that client or staff; a deposit is handed over (retrieve) by the owner of the relay point that
 * holds it, or staff, and only with the recipient's code (checked by the service against the
 * delivery's code).
 */
@RestController
@RequestMapping("/api/relay-deposits")
@RequiredArgsConstructor
public class RelayDepositController {

    private final RelayDepositUseCase relayDepositUseCase;
    private final RelayPointAccessGuard guard;

    /** Deposits held by the caller's own relay points. */
    @GetMapping("/mine")
    public Flux<RelayDeposit> getMyDeposits(@CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.myRelayPoints(ctx)
                .map(GofpRelayPoint::getCoreRelayPointId)
                .distinct()
                .concatMap(relayDepositUseCase::getDepositsByRelayPointId);
    }

    @GetMapping("/relay-point/{relayPointId}")
    public Flux<RelayDeposit> getDepositsByRelayPoint(
            @PathVariable UUID relayPointId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(relayPointId, ctx)
                .flatMapMany(rp -> relayDepositUseCase.getDepositsByRelayPointId(rp.getCoreRelayPointId()));
    }

    @GetMapping("/client/{clientId}")
    public Flux<RelayDeposit> getDepositsByClient(
            @PathVariable UUID clientId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireSelfOrStaff(clientId, ctx)
                .thenMany(Flux.defer(() -> relayDepositUseCase.getDepositsByClientId(clientId)));
    }

    @PatchMapping("/{id}/retrieve")
    public Mono<ResponseEntity<RelayDeposit>> markAsRetrieved(
            @PathVariable UUID id,
            @RequestParam(required = true) String otpCode,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return relayDepositUseCase.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Relay deposit not found: " + id)))
                .flatMap(deposit -> guard.requireOwnerOrStaff(deposit.getRelayPointId(), ctx))
                .then(Mono.defer(() -> relayDepositUseCase.markAsRetrieved(id, otpCode)))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> e instanceof AccessDeniedException
                        ? Mono.error(e)
                        : Mono.just(ResponseEntity.badRequest().build()));
    }
}
