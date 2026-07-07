package com.yowyob.tiibntick.core.resource.application;

import com.yowyob.tiibntick.core.resource.application.port.in.AddFreelancerVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.AssignFreelancerVehicleToMissionCommand;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerEquipmentRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.application.service.FreelancerFleetApplicationService;
import com.yowyob.tiibntick.core.resource.domain.exception.FreelancerFleetCapacityExceededException;
import com.yowyob.tiibntick.core.resource.domain.exception.FreelancerVehicleNotFoundException;
import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FreelancerFleetApplicationService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class FreelancerFleetApplicationServiceTest {

    @Mock private FreelancerVehicleRepository vehicleRepository;
    @Mock private FreelancerEquipmentRepository equipmentRepository;
    @Mock private ResourceEventPublisherPort eventPublisher;

    private FreelancerFleetApplicationService service;

    private UUID orgId;
    private FreelancerVehicle sampleMoto;

    @BeforeEach
    void setUp() {
        service = new FreelancerFleetApplicationService(
                vehicleRepository, equipmentRepository, eventPublisher);
        orgId = UUID.randomUUID();
        sampleMoto = FreelancerVehicle.register(orgId, VehicleType.MOTO, "Honda", "CB 125",
                "CMR-001-AB", 80.0, 0.3, FuelType.ESSENCE, 4.5, null, null);
    }

    @Nested
    @DisplayName("addVehicle")
    class AddVehicle {

        @Test
        @DisplayName("Should add vehicle when fleet has space")
        void shouldAddVehicleWhenFleetHasSpace() {
            when(vehicleRepository.countByOwnerOrgId(orgId)).thenReturn(Mono.just(1L));
            when(vehicleRepository.existsByOwnerOrgIdAndPlateNumber(orgId, "CMR-001-AB")).thenReturn(Mono.just(false));
            when(vehicleRepository.save(any())).thenReturn(Mono.just(sampleMoto));
            when(eventPublisher.publish(any(
                    com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleRegisteredEvent.class)))
                    .thenReturn(Mono.empty());

            AddFreelancerVehicleCommand cmd = new AddFreelancerVehicleCommand(orgId,
                    VehicleType.MOTO, "Honda", "CB 125", "CMR-001-AB", 80.0, 0.3,
                    FuelType.ESSENCE, 4.5, null, null);

            StepVerifier.create(service.addVehicle(cmd))
                    .expectNext(sampleMoto)
                    .verifyComplete();

            verify(vehicleRepository).save(any());
            verify(eventPublisher).publish(any(
                    com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleRegisteredEvent.class));
        }

        @Test
        @DisplayName("Should reject when fleet is full (3 vehicles)")
        void shouldRejectWhenFleetFull() {
            when(vehicleRepository.countByOwnerOrgId(orgId)).thenReturn(Mono.just(3L));
            when(vehicleRepository.existsByOwnerOrgIdAndPlateNumber(orgId, "CMR-004-XX"))
                    .thenReturn(Mono.just(false));

            AddFreelancerVehicleCommand cmd = new AddFreelancerVehicleCommand(orgId,
                    VehicleType.MOTO, "Honda", "CB", "CMR-004-XX", 80.0, null,
                    FuelType.ESSENCE, 4.0, null, null);

            StepVerifier.create(service.addVehicle(cmd))
                    .expectError(FreelancerFleetCapacityExceededException.class)
                    .verify();

            verify(vehicleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject duplicate plate number within same org")
        void shouldRejectDuplicatePlate() {
            when(vehicleRepository.countByOwnerOrgId(orgId)).thenReturn(Mono.just(1L));
            when(vehicleRepository.existsByOwnerOrgIdAndPlateNumber(orgId, "CMR-001-AB"))
                    .thenReturn(Mono.just(true));

            AddFreelancerVehicleCommand cmd = new AddFreelancerVehicleCommand(orgId,
                    VehicleType.MOTO, "Honda", "CB", "CMR-001-AB", 80.0, null,
                    FuelType.ESSENCE, 4.0, null, null);

            StepVerifier.create(service.addVehicle(cmd))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("assignToMission")
    class AssignToMission {

        @Test
        @DisplayName("Should assign available vehicle to mission")
        void shouldAssignAvailableVehicle() {
            when(vehicleRepository.findById(sampleMoto.vehicleId())).thenReturn(Mono.just(sampleMoto));
            FreelancerVehicle onMission = sampleMoto.assignToMission("MISSION-001");
            when(vehicleRepository.save(any())).thenReturn(Mono.just(onMission));
            when(eventPublisher.publish(any(
                    com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleAssignedToMissionEvent.class)))
                    .thenReturn(Mono.empty());

            AssignFreelancerVehicleToMissionCommand cmd =
                    new AssignFreelancerVehicleToMissionCommand(sampleMoto.vehicleId(), orgId, "MISSION-001");

            StepVerifier.create(service.assignToMission(cmd))
                    .expectNextMatches(v -> "MISSION-001".equals(v.currentMissionId()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when vehicle not found")
        void shouldFailWhenVehicleNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(vehicleRepository.findById(unknownId)).thenReturn(Mono.empty());

            AssignFreelancerVehicleToMissionCommand cmd =
                    new AssignFreelancerVehicleToMissionCommand(unknownId, orgId, "M-999");

            StepVerifier.create(service.assignToMission(cmd))
                    .expectError(FreelancerVehicleNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("listFleet")
    class ListFleet {

        @Test
        @DisplayName("Should return all org vehicles")
        void shouldReturnAllVehicles() {
            when(vehicleRepository.findByOwnerOrgId(orgId)).thenReturn(Flux.just(sampleMoto));

            StepVerifier.create(service.getAllVehicles(orgId))
                    .expectNext(sampleMoto)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return only available vehicles")
        void shouldReturnAvailableOnly() {
            when(vehicleRepository.findAvailableByOwnerOrgId(orgId)).thenReturn(Flux.just(sampleMoto));

            StepVerifier.create(service.getAvailableVehicles(orgId))
                    .expectNext(sampleMoto)
                    .verifyComplete();
        }
    }
}
