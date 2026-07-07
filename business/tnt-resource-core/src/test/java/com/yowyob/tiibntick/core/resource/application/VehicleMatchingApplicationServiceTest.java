package com.yowyob.tiibntick.core.resource.application;

import com.yowyob.tiibntick.core.resource.application.port.in.FindBestVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleRepository;
import com.yowyob.tiibntick.core.resource.application.service.VehicleMatchingApplicationService;
import com.yowyob.tiibntick.core.resource.domain.exception.NoAvailableVehicleException;
import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Unit tests for VehicleMatchingApplicationService.
 * Verifies best-fit vehicle selection strategy.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class VehicleMatchingApplicationServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleMatchingApplicationService matchingService;

    private Vehicle buildVehicle(double maxWeightKg, double maxVolumeM3) {
        UUID tenantId = UUID.randomUUID();
        return Vehicle.register(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                "LT-" + (int) maxWeightKg + "-XA", "Toyota", "Hilux",
                2022, VehicleType.VAN, maxWeightKg, maxVolumeM3);
    }

    @Test
    void should_find_best_fit_vehicle() {
        UUID tenantId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        Vehicle small = buildVehicle(50, 0.5);
        Vehicle medium = buildVehicle(200, 2.0);
        Vehicle large  = buildVehicle(500, 5.0);

        when(vehicleRepository.findAvailableByAgency(tenantId, agencyId))
                .thenReturn(Flux.just(small, medium, large));

        FindBestVehicleCommand cmd = new FindBestVehicleCommand(
                tenantId, agencyId, 100.0, 1.0, List.of());

        StepVerifier.create(matchingService.findBestVehicle(cmd))
                .expectNextMatches(v -> v.capacity().maxWeightKg() == 200.0)
                .verifyComplete();
    }

    @Test
    void should_throw_when_no_vehicle_available() {
        UUID tenantId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        when(vehicleRepository.findAvailableByAgency(tenantId, agencyId))
                .thenReturn(Flux.empty());

        FindBestVehicleCommand cmd = new FindBestVehicleCommand(
                tenantId, agencyId, 100.0, 1.0, List.of());

        StepVerifier.create(matchingService.findBestVehicle(cmd))
                .expectError(NoAvailableVehicleException.class)
                .verify();
    }

    @Test
    void should_respect_exclude_list() {
        UUID tenantId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        Vehicle v1 = buildVehicle(200, 2.0);
        Vehicle v2 = buildVehicle(300, 3.0);

        when(vehicleRepository.findAvailableByAgency(tenantId, agencyId))
                .thenReturn(Flux.just(v1, v2));

        FindBestVehicleCommand cmd = new FindBestVehicleCommand(
                tenantId, agencyId, 100.0, 1.0, List.of(v1.id()));

        StepVerifier.create(matchingService.findBestVehicle(cmd))
                .expectNextMatches(v -> v.id().equals(v2.id()))
                .verifyComplete();
    }
}
