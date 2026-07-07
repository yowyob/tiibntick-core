package com.yowyob.tiibntick.core.sync.application.port.out;

import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ISyncSessionRepository {
    Mono<Void> save(SyncSession session);
    Mono<SyncSession> findById(SyncSessionId id);
    Flux<SyncSession> findRecentByUser(String userId, String tenantId, int limit);
    Mono<Long> deleteCompletedBefore(LocalDateTime cutoff);
}
