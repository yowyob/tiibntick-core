package com.yowyob.tiibntick.core.agency.sync.adapter.in.web;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.sync.application.service.AgencySyncService;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencySyncControllerPushContractTest {

    @Mock private AgencySyncService syncService;

    private AgencySyncController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID agencyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new AgencySyncController(syncService);
    }

    @Test
    @DisplayName("POST push forwards path agencyId/userId/device and body operations")
    void pushForwardsContract() {
        when(syncService.push(any())).thenReturn(Mono.just(new SyncPushResponse(
                "s1", 1, 1, 0, 0, 0, List.of(), "tok", "ok")));

        List<Map<String, Object>> ops = List.of(Map.of(
                "id", "op-1",
                "type", "MISSION_STATUS_UPDATE",
                "aggregateType", "MISSION",
                "aggregateId", UUID.randomUUID().toString(),
                "payload", Map.of("delivererId", UUID.randomUUID().toString())));

        StepVerifier.create(controller.push(
                        tenantId, agencyId, userId, "device-9", "sync-token",
                        new AgencySyncController.PushRequest(ops)))
                .assertNext(resp -> assertThat(resp.getData().operationsApplied()).isEqualTo(1))
                .verifyComplete();

        ArgumentCaptor<AgencySyncService.PushInput> captor =
                ArgumentCaptor.forClass(AgencySyncService.PushInput.class);
        verify(syncService).push(captor.capture());
        AgencySyncService.PushInput input = captor.getValue();
        assertThat(input.tenantId()).isEqualTo(tenantId);
        assertThat(input.agencyId()).isEqualTo(agencyId);
        assertThat(input.userId()).isEqualTo(userId);
        assertThat(input.deviceId()).isEqualTo("device-9");
        assertThat(input.syncToken()).isEqualTo("sync-token");
        assertThat(input.operations()).isEqualTo(ops);
    }

    @Test
    @DisplayName("POST push propagates validation failures from service")
    void pushPropagatesValidationError() {
        when(syncService.push(any()))
                .thenReturn(Mono.error(new TntValidationException("Unsupported Agency offline operation type")));

        StepVerifier.create(controller.push(
                        tenantId, agencyId, userId, null, null,
                        new AgencySyncController.PushRequest(List.of(Map.of("type", "GPS_UPDATE")))))
                .expectError(TntValidationException.class)
                .verify();
    }
}
