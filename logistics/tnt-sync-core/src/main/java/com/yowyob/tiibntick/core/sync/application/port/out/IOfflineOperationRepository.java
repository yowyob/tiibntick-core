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

    /**
     * Idempotency check for the push path: {@code true} if an operation with this id was
     * already persisted with status {@link OfflineOpStatus#APPLIED} in a previous push
     * (e.g. the client resubmitted the same op after a dropped connection). Callers that
     * dispatch to a real business use-case (see {@code IOfflineOperationApplier}) must skip
     * re-invoking it when this returns {@code true}, to avoid double side-effects.
     */
    Mono<Boolean> isAlreadyApplied(String operationId);
}
