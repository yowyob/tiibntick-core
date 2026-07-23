package com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.entity.FleetManLinkEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FleetManLinkR2dbcRepository extends ReactiveCrudRepository<FleetManLinkEntity, UUID> {

    Mono<FleetManLinkEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);
}
