package com.yowyob.tiibntick.core.agency.sync.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence.DeviceRegistrationR2dbcRepository;
import com.yowyob.tiibntick.core.agency.sync.application.offline.AgencyOfflinePushGuard;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
import com.yowyob.tiibntick.core.sync.application.port.in.IComputeDeltaUseCase;
import com.yowyob.tiibntick.core.sync.application.port.in.IProcessSyncBatchUseCase;
import com.yowyob.tiibntick.core.sync.application.port.out.IDuckDbSchemaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencySyncServicePushContractTest {

    @Mock private IComputeDeltaUseCase computeDelta;
    @Mock private IProcessSyncBatchUseCase processSyncBatch;
    @Mock private IDuckDbSchemaProvider schemaProvider;
    @Mock private DeviceRegistrationR2dbcRepository deviceRepo;
    @Mock private AgencyRegistryService agencyRegistry;
    @Mock private AgencyOfflinePushGuard offlinePushGuard;

    private AgencySyncService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID agencyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AgencySyncService(
                computeDelta, processSyncBatch, schemaProvider,
                deviceRepo, agencyRegistry, offlinePushGuard, new ObjectMapper());
    }

    @Test
    @DisplayName("push validates ownership before generic batch and preserves operation ids")
    void pushValidatesThenBatches() {
        when(agencyRegistry.getById(tenantId, agencyId))
                .thenReturn(Mono.just(org.mockito.Mockito.mock(AgencyRegistryEntity.class)));
        when(offlinePushGuard.validateBeforeBatch(eq(tenantId), eq(agencyId), anyList()))
                .thenReturn(Mono.empty());
        when(processSyncBatch.processSyncBatch(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(new SyncPushResponse(
                        "session-1", 1, 1, 0, 0, 0, List.of(), "token-next", "ok")));
        when(deviceRepo.findByTenantIdAndAgencyIdAndUserIdAndDeviceId(
                eq(tenantId), eq(agencyId), eq(userId), eq("device-1")))
                .thenReturn(Mono.empty());
        when(deviceRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        String opId = "stable-op-id-1";
        UUID missionId = UUID.randomUUID();
        UUID delivererId = UUID.randomUUID();
        List<Map<String, Object>> ops = List.of(Map.of(
                "id", opId,
                "type", "MISSION_STATUS_UPDATE",
                "aggregateType", "MISSION",
                "aggregateId", missionId.toString(),
                "payload", Map.of("delivererId", delivererId.toString()),
                "localTimestampMs", 1L,
                "sequenceNumber", 1L));

        StepVerifier.create(service.push(new AgencySyncService.PushInput(
                        tenantId, agencyId, userId, "device-1", null, ops)))
                .assertNext(response -> assertThat(response.operationsApplied()).isEqualTo(1))
                .verifyComplete();

        ArgumentCaptor<com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest> captor =
                ArgumentCaptor.forClass(com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest.class);
        verify(offlinePushGuard).validateBeforeBatch(eq(tenantId), eq(agencyId), eq(ops));
        verify(processSyncBatch).processSyncBatch(
                eq(userId.toString()), eq(tenantId.toString()), eq("device-1"), captor.capture());
        assertThat(captor.getValue().operations()).hasSize(1);
        assertThat(captor.getValue().operations().get(0).id()).isEqualTo(opId);
        assertThat(captor.getValue().operations().get(0).type()).isEqualTo("MISSION_STATUS_UPDATE");
    }

    @Test
    @DisplayName("push short-circuits when guard rejects type/schema/ownership")
    void pushStopsWhenGuardFails() {
        when(agencyRegistry.getById(tenantId, agencyId))
                .thenReturn(Mono.just(org.mockito.Mockito.mock(AgencyRegistryEntity.class)));
        when(offlinePushGuard.validateBeforeBatch(eq(tenantId), eq(agencyId), anyList()))
                .thenReturn(Mono.error(new TntValidationException("bad type")));

        StepVerifier.create(service.push(new AgencySyncService.PushInput(
                        tenantId, agencyId, userId, "device-1", null,
                        List.of(Map.of("type", "GPS_UPDATE")))))
                .expectError(TntValidationException.class)
                .verify();

        verify(processSyncBatch, never()).processSyncBatch(anyString(), anyString(), anyString(), any());
    }
}
