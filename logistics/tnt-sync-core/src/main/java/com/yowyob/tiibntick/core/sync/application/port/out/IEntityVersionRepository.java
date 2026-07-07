package com.yowyob.tiibntick.core.sync.application.port.out;

import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

public interface IEntityVersionRepository {
    Mono<Void> upsert(EntityVersionRecord record);
    Mono<EntityVersionRecord> findCurrent(String tenantId, String aggregateType, String aggregateId);
    Flux<EntityVersionRecord> findChangedSince(String tenantId, LocalDateTime since,
                                               Set<String> filterAggregates, int limit);
    Mono<Long> countChangedSince(String tenantId, LocalDateTime since);
    Flux<EntityVersionRecord> findAllCurrentByType(String tenantId, String aggregateType);
}
