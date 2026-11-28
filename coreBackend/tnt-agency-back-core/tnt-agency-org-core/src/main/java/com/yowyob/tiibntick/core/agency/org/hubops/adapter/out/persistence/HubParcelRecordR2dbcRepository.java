package com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity.HubParcelRecordEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface HubParcelRecordR2dbcRepository extends ReactiveCrudRepository<HubParcelRecordEntity, UUID> {

    Flux<HubParcelRecordEntity> findByHubIdAndTenantId(UUID hubId, UUID tenantId);

    Mono<HubParcelRecordEntity> findByTrackingCodeAndTenantId(String trackingCode, UUID tenantId);

    Flux<HubParcelRecordEntity> findByTenantIdAndStatusAndWithdrawalDeadlineBefore(
            UUID tenantId, String status, Instant deadline);
}
