package com.yowyob.tiibntick.core.sync.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpStatus;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class OfflineQueueDomainServiceTest {

    @Mock private IOfflineOperationRepository operationRepository;
    @Mock private IEntityVersionRepository entityVersionRepository;
    @Mock private ISyncEventPublisher eventPublisher;

    private OfflineQueueDomainService service;
    private ConflictResolverService conflictResolver;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        conflictResolver = new ConflictResolverService(ConflictResolverService.Strategy.LWW);
        service = new OfflineQueueDomainService(operationRepository, entityVersionRepository, conflictResolver,
                eventPublisher, List.of(), new ObjectMapper());
        when(operationRepository.save(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    private OfflineOperation buildOp(String aggregateId, LocalDateTime localTs) {
        return new OfflineOperation(
                OfflineOpId.generate(), "user1", "tenant-A", "device1",
                OfflineOpType.MISSION_STATUS_UPDATE,
                "MISSION", aggregateId,
                "{\"status\":\"PICKED_UP\"}", localTs, 1L);
    }

    @Test
    @DisplayName("processOperations() returns empty result for empty list")
    void processEmptyListReturnsEmpty() {
        StepVerifier.create(service.processOperations(List.of(), "session-1"))
                .assertNext(result -> {
                    assertThat(result.applied()).isEqualTo(0);
                    assertThat(result.conflicts()).isEqualTo(0);
                    assertThat(result.discarded()).isEqualTo(0);
                })
                .verifyComplete();

        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOperations() applies new operations without conflict")
    void processOperationsAppliesNew() {
        when(entityVersionRepository.findCurrent("tenant-A", "MISSION", "M-001")).thenReturn(Mono.empty());
        when(entityVersionRepository.upsert(any())).thenReturn(Mono.empty());

        OfflineOperation op = buildOp("M-001", NOW.minusMinutes(5));

        StepVerifier.create(service.processOperations(List.of(op), "session-1"))
                .assertNext(result -> {
                    assertThat(result.applied()).isEqualTo(1);
                    assertThat(result.conflicts()).isEqualTo(0);
                })
                .verifyComplete();

        assertThat(op.getStatus()).isEqualTo(OfflineOpStatus.APPLIED);
    }

    @Test
    @DisplayName("processOperations() detects conflict when server is newer — server wins with LWW")
    void processOperationsDetectsConflictServerWins() {
        EntityVersionRecord serverVersion = new EntityVersionRecord(
                "tenant-A", "MISSION", "M-002", 1000L,
                DeltaOperation.STATUS_CHANGED, "{\"status\":\"IN_TRANSIT\"}",
                NOW.plusMinutes(10), "server-system");

        when(entityVersionRepository.findCurrent("tenant-A", "MISSION", "M-002")).thenReturn(Mono.just(serverVersion));
        when(entityVersionRepository.upsert(any())).thenReturn(Mono.empty());

        OfflineOperation op = buildOp("M-002", NOW); // older than server

        StepVerifier.create(service.processOperations(List.of(op), "session-1"))
                .assertNext(result -> {
                    assertThat(result.conflicts()).isEqualTo(1);
                    assertThat(result.conflictRecords()).hasSize(1);
                    assertThat(result.conflictRecords().get(0).resolution()).isEqualTo(ConflictResolution.SERVER_WINS);
                })
                .verifyComplete();
    }

    /*@Test
    @DisplayName("processOperations() client wins when client is newer")
    void processOperationsClientWinsWhenNewer() {
        EntityVersionRecord serverVersion = new EntityVersionRecord(
                "tenant-A", "MISSION", "M-003", 1000L,
                DeltaOperation.STATUS_CHANGED, "{\"status\":\"CREATED\"}",
                NOW.minusMinutes(30), "server-system");

        when(entityVersionRepository.findCurrent("tenant-A", "MISSION", "M-003")).thenReturn(Mono.just(serverVersion));
        when(entityVersionRepository.upsert(any())).thenReturn(Mono.empty());

        OfflineOperation op = buildOp("M-003", NOW.minusMinutes(10)); // newer than server

        StepVerifier.create(service.processOperations(List.of(op), "session-1"))
                .assertNext(result -> {
                    assertThat(result.conflictRecords().get(0).resolution()).isEqualTo(ConflictResolution.CLIENT_WINS);
                })
                .verifyComplete();

        assertThat(op.getStatus()).isEqualTo(OfflineOpStatus.APPLIED);
    } */

    @Test
    @DisplayName("processOperations() applies client operation when client is newer (no conflict)")
    void processOperationsAppliesWhenClientIsNewer() {
        EntityVersionRecord serverVersion = new EntityVersionRecord(
                "tenant-A", "MISSION", "M-003", 1000L,
                DeltaOperation.STATUS_CHANGED, "{\"status\":\"CREATED\"}",
                NOW.minusMinutes(30), "server-system");

        when(entityVersionRepository.findCurrent("tenant-A", "MISSION", "M-003")).thenReturn(Mono.just(serverVersion));
        when(entityVersionRepository.upsert(any())).thenReturn(Mono.empty());

        OfflineOperation op = buildOp("M-003", NOW.minusMinutes(10)); // newer than server

        StepVerifier.create(service.processOperations(List.of(op), "session-1"))
                .assertNext(result -> {
                    assertThat(result.applied()).isEqualTo(1);
                    assertThat(result.conflicts()).isEqualTo(0);
                    assertThat(result.discarded()).isEqualTo(0);
                    assertThat(result.conflictRecords()).isEmpty();
                })
                .verifyComplete();

        assertThat(op.getStatus()).isEqualTo(OfflineOpStatus.APPLIED);
    }

    @Test
    @DisplayName("processOperations() processes multiple ops in sequence number order")
    void processOperationsRespectSequenceOrder() {
        when(entityVersionRepository.findCurrent(any(), any(), any())).thenReturn(Mono.empty());
        when(entityVersionRepository.upsert(any())).thenReturn(Mono.empty());

        OfflineOperation op1 = new OfflineOperation(OfflineOpId.generate(), "user1", "tenant-A", "d1",
                OfflineOpType.GPS_UPDATE, "GPS", "gps-1", "{}", NOW.minusMinutes(2), 1L);
        OfflineOperation op2 = new OfflineOperation(OfflineOpId.generate(), "user1", "tenant-A", "d1",
                OfflineOpType.MISSION_STATUS_UPDATE, "MISSION", "M-001", "{}", NOW.minusMinutes(1), 2L);
        OfflineOperation op3 = new OfflineOperation(OfflineOpId.generate(), "user1", "tenant-A", "d1",
                OfflineOpType.PACKAGE_SCAN, "PACKAGE", "PKG-001", "{}", NOW, 3L);

        // Submit in reverse order
        StepVerifier.create(service.processOperations(List.of(op3, op1, op2), "session-order"))
                .assertNext(result -> assertThat(result.applied()).isEqualTo(3))
                .verifyComplete();
    }
}
