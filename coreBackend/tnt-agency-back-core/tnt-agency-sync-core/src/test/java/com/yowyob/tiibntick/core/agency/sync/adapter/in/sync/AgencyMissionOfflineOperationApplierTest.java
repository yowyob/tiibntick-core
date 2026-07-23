package com.yowyob.tiibntick.core.agency.sync.adapter.in.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntUnauthorizedException;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.DelivererResponse;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyMissionOfflineOperationApplierTest {

    @Mock MissionService missionService;
    @Mock AgencyDelivererService delivererService;

    AgencyMissionOfflineOperationApplier applier;

    final UUID tenantId = UUID.randomUUID();
    final UUID agencyId = UUID.randomUUID();
    final UUID missionId = UUID.randomUUID();
    final UUID delivererId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applier = new AgencyMissionOfflineOperationApplier(
                missionService, delivererService, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("supports only MISSION aggregate")
    void supportsMissionOnly() {
        assertThat(applier.supports("MISSION")).isTrue();
        assertThat(applier.supports("PACKAGE")).isFalse();
        assertThat(applier.supports("GPS")).isFalse();
    }

    @Test
    @DisplayName("MISSION_STATUS_UPDATE dispatches pickup")
    void appliesPickup() {
        stubAuthorization(delivererId);
        AgencyMission mission = AgencyMission.create(
                missionId, tenantId, agencyId, UUID.randomUUID(), Instant.now(), Instant.now());
        mission.assign(delivererId, null, Instant.now());
        when(missionService.pickup(tenantId, missionId, delivererId)).thenReturn(Mono.just(mission));

        OfflineOperation op = operation(
                OfflineOpType.MISSION_STATUS_UPDATE,
                "{\"delivererId\":\"" + delivererId + "\"}");

        StepVerifier.create(applier.apply(op))
                .assertNext(json -> assertThat(json).contains(missionId.toString()))
                .verifyComplete();

        verify(missionService).pickup(tenantId, missionId, delivererId);
    }

    @Test
    @DisplayName("DELIVERY_CONFIRMATION dispatches deliver")
    void appliesDelivery() {
        stubAuthorization(delivererId);
        AgencyMission mission = AgencyMission.create(
                missionId, tenantId, agencyId, UUID.randomUUID(), Instant.now(), Instant.now());
        when(missionService.deliver(tenantId, missionId, delivererId, "PROOF-1"))
                .thenReturn(Mono.just(mission));

        OfflineOperation op = operation(
                OfflineOpType.DELIVERY_CONFIRMATION,
                "{\"delivererId\":\"" + delivererId + "\",\"proofReference\":\"PROOF-1\"}");

        StepVerifier.create(applier.apply(op))
                .assertNext(json -> assertThat(json).contains(missionId.toString()))
                .verifyComplete();
    }

    @Test
    @DisplayName("rejects deliverer not assigned to mission")
    void rejectsForeignDeliverer() {
        UUID otherDeliverer = UUID.randomUUID();
        when(delivererService.getById(tenantId, delivererId)).thenReturn(Mono.just(
                new DelivererResponse(delivererId, tenantId, agencyId, null, null, null, "AVAILABLE", Instant.now(), null)));
        AgencyMission foreignMission = AgencyMission.create(
                missionId, tenantId, agencyId, UUID.randomUUID(), Instant.now(), Instant.now());
        foreignMission.assign(otherDeliverer, null, Instant.now());
        when(missionService.getById(tenantId, missionId)).thenReturn(Mono.just(foreignMission));

        OfflineOperation op = operation(
                OfflineOpType.DELIVERY_CONFIRMATION,
                "{\"delivererId\":\"" + delivererId + "\",\"proofReference\":\"pod\"}");

        StepVerifier.create(applier.apply(op))
                .expectError(TntUnauthorizedException.class)
                .verify();
    }

    private void stubAuthorization(UUID assignedDelivererId) {
        when(delivererService.getById(tenantId, delivererId)).thenReturn(Mono.just(
                new DelivererResponse(delivererId, tenantId, agencyId, null, null, null, "AVAILABLE", Instant.now(), null)));
        AgencyMission mission = AgencyMission.create(
                missionId, tenantId, agencyId, UUID.randomUUID(), Instant.now(), Instant.now());
        mission.assign(assignedDelivererId, null, Instant.now());
        when(missionService.getById(tenantId, missionId)).thenReturn(Mono.just(mission));
    }

    private OfflineOperation operation(OfflineOpType type, String payload) {
        return new OfflineOperation(
                OfflineOpId.of("op-" + type.name()),
                "user-1",
                tenantId.toString(),
                "device-1",
                type,
                "MISSION",
                missionId.toString(),
                payload,
                LocalDateTime.now(),
                1L);
    }
}
