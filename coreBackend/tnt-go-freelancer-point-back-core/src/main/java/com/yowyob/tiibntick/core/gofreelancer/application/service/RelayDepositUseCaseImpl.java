package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.RelayDepositUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RelayDepositUseCaseImpl implements RelayDepositUseCase {

    private final RelayDepositService relayDepositService;

    @Override
    public Mono<RelayDeposit> createRelayDeposit(UUID packetId, UUID clientId, UUID relayPointId, Double storageFee) {
        return relayDepositService.createRelayDeposit(packetId, clientId, relayPointId, storageFee);
    }

    @Override public Flux<RelayDeposit> getDepositsByRelayPointId(UUID id) { return relayDepositService.getDepositsByRelayPointId(id); }
    @Override public Flux<RelayDeposit> getDepositsByClientId(UUID id) { return relayDepositService.getDepositsByClientId(id); }
    @Override public Mono<RelayDeposit> markAsRetrieved(UUID id, String otpCode) { return relayDepositService.markAsRetrieved(id, otpCode); }
}
