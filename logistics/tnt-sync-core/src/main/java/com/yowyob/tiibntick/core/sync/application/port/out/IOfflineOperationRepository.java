package com.yowyob.tiibntick.core.sync.application.port.out;

import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IOfflineOperationRepository {
    Mono<Void> save(OfflineOperation operation);
    Mono<Void> saveAll(java.util.List<OfflineOperation> operations);
    Flux<OfflineOperation> findPendingByUser(String userId, String tenantId);
    Flux<OfflineOperation> findBySessionId(String sessionId);
    Mono<Void> updateStatus(String operationId, OfflineOpStatus status, String error);
}
