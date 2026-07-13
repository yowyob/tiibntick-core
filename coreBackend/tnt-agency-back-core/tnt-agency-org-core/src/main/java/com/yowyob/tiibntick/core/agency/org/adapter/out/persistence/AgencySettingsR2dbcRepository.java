package com.yowyob.tiibntick.core.agency.org.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencySettingsEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencySettingsR2dbcRepository extends ReactiveCrudRepository<AgencySettingsEntity, UUID> {

    Mono<AgencySettingsEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);
}
