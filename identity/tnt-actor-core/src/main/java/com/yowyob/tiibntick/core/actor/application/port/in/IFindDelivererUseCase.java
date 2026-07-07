package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IFindDelivererUseCase {

    Mono<DelivererProfile> findByActorId(UUID tenantId, UUID actorId);

    Flux<DelivererProfile> findByAgency(UUID tenantId, UUID agencyId);

    Flux<DelivererProfile> findByBranch(UUID tenantId, UUID branchId);

    Flux<DelivererProfile> findAvailableInBranch(UUID tenantId, UUID branchId);
}
