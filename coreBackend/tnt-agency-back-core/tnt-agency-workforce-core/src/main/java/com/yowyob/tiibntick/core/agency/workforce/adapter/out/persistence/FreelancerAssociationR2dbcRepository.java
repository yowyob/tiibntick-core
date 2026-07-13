package com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.FreelancerAssociationEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FreelancerAssociationR2dbcRepository
        extends ReactiveCrudRepository<FreelancerAssociationEntity, UUID> {

    Mono<FreelancerAssociationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<FreelancerAssociationEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);
}
