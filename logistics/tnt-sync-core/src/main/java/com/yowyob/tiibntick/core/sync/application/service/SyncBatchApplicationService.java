package com.yowyob.tiibntick.core.sync.application.service;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
import com.yowyob.tiibntick.core.sync.application.port.in.IProcessSyncBatchUseCase;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.event.SyncCompletedEvent;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.service.DeltaSyncDomainService;
import com.yowyob.tiibntick.core.sync.domain.service.OfflineQueueDomainService;
import com.yowyob.tiibntick.core.sync.domain.service.SyncSessionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class SyncBatchApplicationService implements IProcessSyncBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(SyncBatchApplicationService.class);

    private final OfflineQueueDomainService offlineQueueService;
    private final DeltaSyncDomainService deltaSyncService;
    private final SyncSessionManager sessionManager;
    private final ISyncEventPublisher eventPublisher;
    private final Counter pushSyncCounter;

    public SyncBatchApplicationService(OfflineQueueDomainService offlineQueueService,
                                       DeltaSyncDomainService deltaSyncService,
                                       SyncSessionManager sessionManager,
                                       ISyncEventPublisher eventPublisher,
                                       MeterRegistry meterRegistry) {
        this.offlineQueueService = offlineQueueService;
        this.deltaSyncService = deltaSyncService;
        this.sessionManager = sessionManager;
        this.eventPublisher = eventPublisher;
        this.pushSyncCounter = Counter.builder("tnt.sync.push.total")
                .description("Total push-sync requests processed")
                .register(meterRegistry);
    }

    @Override
    public Mono<SyncPushResponse> processSyncBatch(String userId, String tenantId, String deviceId,
                                                    SyncPushRequest request) {
        pushSyncCounter.increment();

        SyncToken sinceToken = parseSyncToken(request.lastSyncToken(), userId, tenantId, deviceId);

        return sessionManager.open(userId, tenantId, deviceId, sinceToken)
                .flatMap(session -> processBatch(session, userId, tenantId, deviceId, request))
                .doOnError(ex -> log.error("Push sync failed for user={}, tenant={}: {}", userId, tenantId, ex.getMessage()));
    }

    private Mono<SyncPushResponse> processBatch(SyncSession session, String userId, String tenantId,
                                                  String deviceId, SyncPushRequest request) {
        List<OfflineOperation> operations = request.hasOperations()
                ? request.operations().stream()
                        .map(dto -> dto.toDomain(userId, tenantId, deviceId))
                        .toList()
                : Collections.emptyList();

        return offlineQueueService.processOperations(operations, session.getId().value())
                .flatMap(result -> {
                    session.recordPushStats(operations.size(), result.applied(), result.conflicts(), result.conflicts());

                    return deltaSyncService.computeDelta(session.getSinceToken(), tenantId, null)
                            .flatMap(delta -> {
                                SyncToken newToken = delta.newToken();
                                session.complete(newToken);

                                SyncCompletedEvent event = new SyncCompletedEvent(
                                        tenantId, session.getId().value(), userId, deviceId,
                                        result.applied(), result.conflicts(),
                                        delta.recordCount(), newToken.value());

                                return eventPublisher.publish(event)
                                        .then(sessionManager.complete(session, newToken))
                                        .thenReturn(buildResponse(session, result, newToken));
                            });
                });
    }

    private SyncPushResponse buildResponse(SyncSession session,
                                            OfflineQueueDomainService.ProcessResult result,
                                            SyncToken newToken) {
        List<SyncPushResponse.ConflictDto> conflictDtos = result.conflictRecords().stream()
                .map(c -> new SyncPushResponse.ConflictDto(
                        c.aggregateType(), c.aggregateId(),
                        c.resolution().name(), c.resolvedValue()))
                .toList();

        return new SyncPushResponse(
                session.getId().value(),
                session.getOperationsSubmitted(),
                session.getOperationsApplied(),
                session.getConflictsDetected(),
                session.getConflictsResolved(),
                result.discarded(),
                conflictDtos,
                newToken.value(),
                "Sync completed"
        );
    }

    private SyncToken parseSyncToken(String tokenValue, String userId, String tenantId, String deviceId) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return SyncToken.initial(userId, tenantId, deviceId);
        }
        return new SyncToken(tokenValue, userId, tenantId, deviceId, LocalDateTime.now().minusHours(1));
    }
}
