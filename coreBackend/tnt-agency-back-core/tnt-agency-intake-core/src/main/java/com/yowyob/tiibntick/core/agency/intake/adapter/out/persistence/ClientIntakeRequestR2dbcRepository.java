package com.yowyob.tiibntick.core.agency.intake.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.intake.adapter.out.persistence.entity.ClientIntakeRequestEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ClientIntakeRequestR2dbcRepository
        extends ReactiveCrudRepository<ClientIntakeRequestEntity, UUID> {

    Mono<ClientIntakeRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<ClientIntakeRequestEntity> findByReferenceCode(String referenceCode);

    Flux<ClientIntakeRequestEntity> findByAgencyIdAndTenantIdAndStatusOrderByCreatedAtDesc(
            UUID agencyId, UUID tenantId, String status);
}
