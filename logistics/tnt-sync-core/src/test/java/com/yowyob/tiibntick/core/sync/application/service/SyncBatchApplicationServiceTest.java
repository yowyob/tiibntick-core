package com.yowyob.tiibntick.core.sync.application.service;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.OfflineOpDto;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.model.SyncDelta;
import com.yowyob.tiibntick.core.sync.domain.model.SyncSession;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.service.DeltaSyncDomainService;
import com.yowyob.tiibntick.core.sync.domain.service.OfflineQueueDomainService;
import com.yowyob.tiibntick.core.sync.domain.service.SyncSessionManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncBatchApplicationServiceTest {

    @Mock private OfflineQueueDomainService offlineQueueService;
    @Mock private DeltaSyncDomainService deltaSyncService;
    @Mock private SyncSessionManager sessionManager;
    @Mock private ISyncEventPublisher eventPublisher;

    private SyncBatchApplicationService service;

    private static final String USER_ID = "user-001";
    private static final String TENANT_ID = "tenant-A";
    private static final String DEVICE_ID = "device-001";

    @BeforeEach
    void setUp() {
        service = new SyncBatchApplicationService(
                offlineQueueService, deltaSyncService, sessionManager,
                eventPublisher, new SimpleMeterRegistry());

        when(sessionManager.open(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    SyncToken token = inv.getArgument(3);
                    SyncSession session = new SyncSession(
                            com.yowyob.tiibntick.core.sync.domain.model.SyncSessionId.generate(),
                            USER_ID, TENANT_ID, DEVICE_ID, token);
                    return Mono.just(session);
                });

        when(sessionManager.complete(any(), any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    private SyncToken buildNextToken() {
        return SyncToken.next(USER_ID, TENANT_ID, DEVICE_ID, LocalDateTime.now());
    }

    private SyncDelta buildEmptyDelta() {
        SyncToken token = buildNextToken();
        return SyncDelta.empty(TENANT_ID, USER_ID, token, token);
    }

    @Test
    @DisplayName("processSyncBatch() with empty operations returns zero stats")
    void processBatchWithNoOperations() {
        when(offlineQueueService.processOperations(anyList(), anyString()))
                .thenReturn(Mono.just(OfflineQueueDomainService.ProcessResult.empty()));
        when(deltaSyncService.computeDelta(any(), any(), any()))
                .thenReturn(Mono.just(buildEmptyDelta()));

        SyncPushRequest request = new SyncPushRequest(null, Collections.emptyList());

        StepVerifier.create(service.processSyncBatch(USER_ID, TENANT_ID, DEVICE_ID, request))
                .assertNext(response -> {
                    assertThat(response.operationsSubmitted()).isEqualTo(0);
                    assertThat(response.operationsApplied()).isEqualTo(0);
                    assertThat(response.conflictsDetected()).isEqualTo(0);
                    assertThat(response.newSyncToken()).isNotBlank();
                    assertThat(response.sessionId()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("processSyncBatch() maps operations from DTOs and returns response")
    void processBatchWithOperations() {
        OfflineOpDto dto = new OfflineOpDto(
                null, "GPS_UPDATE", "GPS", "gps-001",
                "{\"lat\":3.848,\"lon\":11.502}", System.currentTimeMillis(), 1L);

        when(offlineQueueService.processOperations(anyList(), anyString()))
                .thenReturn(Mono.just(new OfflineQueueDomainService.ProcessResult(
                        1, 0, 0, Collections.emptyList())));

        when(deltaSyncService.computeDelta(any(), any(), any()))
                .thenReturn(Mono.just(buildEmptyDelta()));

        SyncPushRequest request = new SyncPushRequest(null, List.of(dto));

        StepVerifier.create(service.processSyncBatch(USER_ID, TENANT_ID, DEVICE_ID, request))
                .assertNext(response -> {
                    assertThat(response.operationsSubmitted()).isEqualTo(1);
                    assertThat(response.conflicts()).isEmpty();
                    assertThat(response.message()).contains("Sync completed");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("processSyncBatch() propagates conflict details in response")
    void processBatchWithConflicts() {
        var conflictRecord = new com.yowyob.tiibntick.core.sync.domain.model.ConflictRecord(
                "MISSION", "M-001",
                "{\"status\":\"PICKED_UP\"}", "{\"status\":\"IN_TRANSIT\"}",
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now(),
                com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution.SERVER_WINS,
                "{\"status\":\"IN_TRANSIT\"}"
        );

        when(offlineQueueService.processOperations(anyList(), anyString()))
                .thenReturn(Mono.just(new OfflineQueueDomainService.ProcessResult(
                        0, 1, 0, List.of(conflictRecord))));
        when(deltaSyncService.computeDelta(any(), any(), any()))
                .thenReturn(Mono.just(buildEmptyDelta()));

        OfflineOpDto dto = new OfflineOpDto(null, "MISSION_STATUS_UPDATE", "MISSION", "M-001",
                "{\"status\":\"PICKED_UP\"}", System.currentTimeMillis() - 60000L, 1L);
        SyncPushRequest request = new SyncPushRequest(null, List.of(dto));

        StepVerifier.create(service.processSyncBatch(USER_ID, TENANT_ID, DEVICE_ID, request))
                .assertNext(response -> {
                    assertThat(response.conflictsDetected()).isEqualTo(1);
                    assertThat(response.conflicts()).hasSize(1);
                    assertThat(response.conflicts().get(0).resolution()).isEqualTo("SERVER_WINS");
                })
                .verifyComplete();
    }
}
