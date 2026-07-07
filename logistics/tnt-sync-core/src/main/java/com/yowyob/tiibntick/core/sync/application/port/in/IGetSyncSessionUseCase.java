package com.yowyob.tiibntick.core.sync.application.port.in;

import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IGetSyncSessionUseCase {
    Mono<SyncSession> findById(SyncSessionId id);
    Flux<SyncSession> findRecentByUser(String userId, String tenantId, int limit);
}
