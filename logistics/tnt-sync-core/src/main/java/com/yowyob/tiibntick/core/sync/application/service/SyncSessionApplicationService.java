package com.yowyob.tiibntick.core.sync.application.service;

import com.yowyob.tiibntick.core.sync.application.port.in.IGetSyncSessionUseCase;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import com.yowyob.tiibntick.core.sync.domain.service.SyncSessionManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SyncSessionApplicationService implements IGetSyncSessionUseCase {

    private final SyncSessionManager sessionManager;

    public SyncSessionApplicationService(SyncSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public Mono<SyncSession> findById(SyncSessionId id) {
        return sessionManager.findById(id);
    }

    @Override
    public Flux<SyncSession> findRecentByUser(String userId, String tenantId, int limit) {
        return sessionManager.findRecentByUser(userId, tenantId, limit);
    }
}
