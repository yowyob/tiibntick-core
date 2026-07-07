package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IFindFreelancerUseCase {

    Mono<FreelancerProfile> findByActorId(UUID tenantId, UUID actorId);

    Flux<FreelancerProfile> findAvailableInZone(UUID tenantId, ServiceZoneId serviceZoneId);

    Flux<FreelancerProfile> findByAssociatedAgency(UUID tenantId, UUID agencyId);
}
