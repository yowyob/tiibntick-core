package com.yowyob.tiibntick.core.agency.sync.application.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntUnauthorizedException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.AgencyMissionR2dbcRepository;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.entity.AgencyMissionEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.DelivererR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.DelivererEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyOfflinePushGuardTest {

    @Mock private AgencyMissionR2dbcRepository missionRepo;
    @Mock private DelivererR2dbcRepository delivererRepo;
    @Mock private AgencyRelayHubR2dbcRepository hubRepo;

    private AgencyOfflinePushGuard guard;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID agencyId = UUID.randomUUID();
    private final UUID otherAgencyId = UUID.randomUUID();
    private final UUID missionId = UUID.randomUUID();
    private final UUID delivererId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new AgencyOfflinePushGuard(missionRepo, delivererRepo, hubRepo, new ObjectMapper());
    }

    @Test
    @DisplayName("rejects unknown or non-exact offline type before batch")
    void rejectsBadType() {
        StepVerifier.create(guard.validateBeforeBatch(tenantId, agencyId, List.of(
                        Map.of("type", "pickup",
                                "aggregateType", "MISSION",
                                "aggregateId", missionId.toString(),
                                "payload", Map.of("delivererId", delivererId.toString())))))
                .expectError(TntValidationException.class)
                .verify();
    }

    @Test
    @DisplayName("rejects mission belonging to another agency")
    void rejectsForeignMission() {
        AgencyMissionEntity mission = new AgencyMissionEntity();
        mission.setId(missionId);
        mission.setTenantId(tenantId);
        mission.setAgencyId(otherAgencyId);
        when(missionRepo.findByIdAndTenantId(eq(missionId), eq(tenantId)))
                .thenReturn(Mono.just(mission));

        StepVerifier.create(guard.validateBeforeBatch(tenantId, agencyId, List.of(pickupOp())))
                .expectError(TntUnauthorizedException.class)
                .verify();
    }

    @Test
    @DisplayName("accepts owned mission + deliverer for pickup")
    void acceptsOwnedPickup() {
        stubOwnedMissionAndDeliverer();

        StepVerifier.create(guard.validateBeforeBatch(tenantId, agencyId, List.of(pickupOp())))
                .verifyComplete();
    }

    @Test
    @DisplayName("rejects hub deposit when hub is outside agency")
    void rejectsForeignHub() {
        stubOwnedMissionAndDeliverer();
        AgencyRelayHubEntity hub = new AgencyRelayHubEntity();
        hub.setId(hubId);
        hub.setTenantId(tenantId);
        hub.setAgencyId(otherAgencyId);
        when(hubRepo.findByIdAndTenantId(eq(hubId), eq(tenantId))).thenReturn(Mono.just(hub));

        Map<String, Object> op = Map.of(
                "type", "HUB_DEPOSIT",
                "aggregateType", "MISSION",
                "aggregateId", missionId.toString(),
                "payload", Map.of(
                        "delivererId", delivererId.toString(),
                        "hubId", hubId.toString()));

        StepVerifier.create(guard.validateBeforeBatch(tenantId, agencyId, List.of(op)))
                .expectError(TntUnauthorizedException.class)
                .verify();
    }

    @Test
    @DisplayName("accepts owned deliverer GPS update")
    void acceptsOwnedGps() {
        DelivererEntity deliverer = new DelivererEntity();
        deliverer.setId(delivererId);
        deliverer.setTenantId(tenantId);
        deliverer.setAgencyId(agencyId);
        when(delivererRepo.findByIdAndTenantId(eq(delivererId), eq(tenantId)))
                .thenReturn(Mono.just(deliverer));

        Map<String, Object> op = Map.of(
                "type", "GPS_UPDATE",
                "aggregateType", "GPS",
                "aggregateId", delivererId.toString(),
                "payload", Map.of(
                        "delivererId", delivererId.toString(),
                        "latitude", 3.8,
                        "longitude", 11.5,
                        "accuracyMeters", 10,
                        "speedKmh", 20,
                        "bearing", 45,
                        "timestamp", "2026-07-17T10:00:00Z"));

        StepVerifier.create(guard.validateBeforeBatch(tenantId, agencyId, List.of(op)))
                .verifyComplete();
    }

    private Map<String, Object> pickupOp() {
        return Map.of(
                "type", "MISSION_STATUS_UPDATE",
                "aggregateType", "MISSION",
                "aggregateId", missionId.toString(),
                "payload", Map.of("delivererId", delivererId.toString()));
    }

    private void stubOwnedMissionAndDeliverer() {
        AgencyMissionEntity mission = new AgencyMissionEntity();
        mission.setId(missionId);
        mission.setTenantId(tenantId);
        mission.setAgencyId(agencyId);
        when(missionRepo.findByIdAndTenantId(eq(missionId), eq(tenantId)))
                .thenReturn(Mono.just(mission));

        DelivererEntity deliverer = new DelivererEntity();
        deliverer.setId(delivererId);
        deliverer.setTenantId(tenantId);
        deliverer.setAgencyId(agencyId);
        when(delivererRepo.findByIdAndTenantId(eq(delivererId), eq(tenantId)))
                .thenReturn(Mono.just(deliverer));
    }
}
