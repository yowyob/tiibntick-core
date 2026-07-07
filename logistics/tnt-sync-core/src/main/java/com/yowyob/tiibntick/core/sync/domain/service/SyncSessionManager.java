package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.application.port.out.ISyncSessionRepository;
import com.yowyob.tiibntick.core.sync.domain.exception.SyncSessionNotFoundException;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

public class SyncSessionManager {

    private static final Logger log = LoggerFactory.getLogger(SyncSessionManager.class);

    private final ISyncSessionRepository sessionRepository;

    public SyncSessionManager(ISyncSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Mono<SyncSession> open(String userId, String tenantId, String deviceId, SyncToken sinceToken) {
        SyncSession session = new SyncSession(
                SyncSessionId.generate(), userId, tenantId, deviceId, sinceToken);
        log.debug("Opening sync session {} for user={}, tenant={}", session.getId(), userId, tenantId);
        return sessionRepository.save(session).thenReturn(session);
    }

    public Mono<SyncSession> findById(SyncSessionId id) {
        return sessionRepository.findById(id)
                .switchIfEmpty(Mono.error(new SyncSessionNotFoundException(id.value())));
    }

    public Mono<SyncSession> complete(SyncSession session, SyncToken resultToken) {
        session.complete(resultToken);
        log.info("Sync session {} completed: {}/{} ops, {} conflicts",
                session.getId(), session.getOperationsApplied(),
                session.getOperationsSubmitted(), session.getConflictsDetected());
        return sessionRepository.save(session).thenReturn(session);
    }

    public Mono<SyncSession> fail(SyncSession session, String reason) {
        session.fail(reason);
        log.warn("Sync session {} failed: {}", session.getId(), reason);
        return sessionRepository.save(session).thenReturn(session);
    }

    public Flux<SyncSession> findRecentByUser(String userId, String tenantId, int limit) {
        return sessionRepository.findRecentByUser(userId, tenantId, limit);
    }

    public Mono<Void> cleanupExpired(Duration retention) {
        LocalDateTime cutoff = LocalDateTime.now().minus(retention);
        return sessionRepository.deleteCompletedBefore(cutoff)
                .doOnNext(count -> {
                    if (count > 0) log.info("Cleaned up {} expired sync sessions", count);
                })
                .then();
    }
}
