package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayDepositEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IRelayDepositUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.ILogisticsPricingRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IRelayDepositRepository;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.RelayDepositStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelayDepositCoreService implements IRelayDepositUseCase {

    private final IRelayDepositRepository     relayDepositRepository;
    private final ILogisticsPricingRepository logisticsPricingRepository;

    @Override
    public Mono<RelayDepositEntity> createDeposit(UUID packetId, UUID clientActorId,
                                                    UUID relayHubId, UUID freelancerActorId,
                                                    UUID deliveryId) {
        return logisticsPricingRepository.findByRelayHubId(relayHubId)
            .map(pricing -> RelayDepositEntity.builder()
                .id(UUID.randomUUID())
                .packetId(packetId)
                .clientActorId(clientActorId)
                .relayHubId(relayHubId)
                .freelancerActorId(freelancerActorId)
                .deliveryId(deliveryId)
                .status(RelayDepositStatus.DEPOSITED.name())
                .storageFee(pricing.getBaseFee())
                .penaltyFee(0.0)
                .gracePeriodDays(pricing.getGracePeriodDays())
                .depositedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
            .switchIfEmpty(Mono.just(RelayDepositEntity.builder()
                .id(UUID.randomUUID())
                .packetId(packetId).clientActorId(clientActorId).relayHubId(relayHubId)
                .freelancerActorId(freelancerActorId).deliveryId(deliveryId)
                .status(RelayDepositStatus.DEPOSITED.name())
                .storageFee(0.0).penaltyFee(0.0).gracePeriodDays(0)
                .depositedAt(Instant.now()).createdAt(Instant.now()).updatedAt(Instant.now())
                .build()))
            .flatMap(relayDepositRepository::save);
    }

    @Override
    public Mono<RelayDepositEntity> markRetrieved(UUID depositId) {
        return relayDepositRepository.findById(depositId)
            .flatMap(deposit -> {
                deposit.setStatus(RelayDepositStatus.RETRIEVED.name());
                deposit.setRetrievedAt(Instant.now());
                deposit.setUpdatedAt(Instant.now());
                return relayDepositRepository.save(deposit);
            });
    }

    @Override
    public Mono<Double> calculatePenalty(UUID depositId) {
        return relayDepositRepository.findById(depositId)
            .flatMap(deposit -> logisticsPricingRepository.findByRelayHubId(deposit.getRelayHubId())
                .map(pricing -> {
                    long daysStored = Duration.between(deposit.getDepositedAt(), Instant.now()).toDays();
                    long billableDays = Math.max(0, daysStored - pricing.getGracePeriodDays());
                    return billableDays > 0 ? billableDays * pricing.getPenaltyPerDay() : 0.0;
                })
                .defaultIfEmpty(0.0));
    }

    @Override public Mono<RelayDepositEntity>  findById(UUID id)                  { return relayDepositRepository.findById(id); }
    @Override public Flux<RelayDepositEntity>  findByRelayHubId(UUID relayHubId)  { return relayDepositRepository.findByRelayHubId(relayHubId); }
    @Override public Flux<RelayDepositEntity>  findByClientActorId(UUID clientActorId) { return relayDepositRepository.findByClientActorId(clientActorId); }
}
