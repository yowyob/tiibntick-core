package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.RelayDepositUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/relay-deposits")
@RequiredArgsConstructor
public class RelayDepositController {

    private final RelayDepositUseCase relayDepositUseCase;

    @GetMapping("/relay-point/{relayPointId}")
    public Flux<RelayDeposit> getDepositsByRelayPoint(@PathVariable UUID relayPointId) {
        return relayDepositUseCase.getDepositsByRelayPointId(relayPointId);
    }

    @GetMapping("/client/{clientId}")
    public Flux<RelayDeposit> getDepositsByClient(@PathVariable UUID clientId) {
        return relayDepositUseCase.getDepositsByClientId(clientId);
    }

    @PatchMapping("/{id}/retrieve")
    public Mono<ResponseEntity<RelayDeposit>> markAsRetrieved(
            @PathVariable UUID id,
            @RequestParam(required = true) String otpCode) {
        return relayDepositUseCase.markAsRetrieved(id, otpCode)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
