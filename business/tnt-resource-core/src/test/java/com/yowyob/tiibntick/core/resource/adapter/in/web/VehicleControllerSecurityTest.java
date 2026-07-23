package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * Regression test for Audit n°7 · #6 — {@code VehicleController}'s 10 mutation endpoints
 * (create/assign/unassign/maintenance lifecycle/retire/odometer/location/alert) had zero
 * {@code @PreAuthorize}/{@code @RequirePermission} guard.
 *
 * <p>Same pattern as {@code MarketCampaignControllerSecurityTest} (coreBackend/tnt-market-back-core)
 * and {@code ProductControllerSecurityTest} (tnt-product-core): wires a minimal Spring
 * context with {@code @EnableReactiveMethodSecurity} around the real controller bean, with
 * no {@code Authentication} in the reactive security context. Before the
 * {@code @PreAuthorize("isAuthenticated()")} fix, these calls reached the use case with no
 * check at all.
 *
 * @author MANFOUO Braun
 */
@SpringJUnitConfig(classes = VehicleControllerSecurityTest.TestConfig.class)
class VehicleControllerSecurityTest {

    @Configuration
    @EnableReactiveMethodSecurity
    static class TestConfig {
        @Bean CreateVehicleUseCase createVehicleUseCase() { return mock(CreateVehicleUseCase.class); }
        @Bean GetVehicleUseCase getVehicleUseCase() { return mock(GetVehicleUseCase.class); }
        @Bean ListVehiclesByAgencyUseCase listVehiclesByAgencyUseCase() { return mock(ListVehiclesByAgencyUseCase.class); }
        @Bean AssignVehicleUseCase assignVehicleUseCase() { return mock(AssignVehicleUseCase.class); }
        @Bean UnassignVehicleUseCase unassignVehicleUseCase() { return mock(UnassignVehicleUseCase.class); }
        @Bean SendVehicleToMaintenanceUseCase sendVehicleToMaintenanceUseCase() { return mock(SendVehicleToMaintenanceUseCase.class); }
        @Bean CompleteVehicleMaintenanceUseCase completeVehicleMaintenanceUseCase() { return mock(CompleteVehicleMaintenanceUseCase.class); }
        @Bean RetireVehicleUseCase retireVehicleUseCase() { return mock(RetireVehicleUseCase.class); }
        @Bean UpdateVehicleOdometerUseCase updateVehicleOdometerUseCase() { return mock(UpdateVehicleOdometerUseCase.class); }
        @Bean UpdateVehicleLocationUseCase updateVehicleLocationUseCase() { return mock(UpdateVehicleLocationUseCase.class); }
        @Bean FindBestVehicleForMissionUseCase findBestVehicleForMissionUseCase() { return mock(FindBestVehicleForMissionUseCase.class); }
        @Bean CheckMaintenanceDueUseCase checkMaintenanceDueUseCase() { return mock(CheckMaintenanceDueUseCase.class); }
        @Bean ScheduleMaintenanceAlertUseCase scheduleMaintenanceAlertUseCase() { return mock(ScheduleMaintenanceAlertUseCase.class); }

        @Bean
        VehicleController vehicleController(
                CreateVehicleUseCase createVehicleUseCase,
                GetVehicleUseCase getVehicleUseCase,
                ListVehiclesByAgencyUseCase listVehiclesUseCase,
                AssignVehicleUseCase assignVehicleUseCase,
                UnassignVehicleUseCase unassignVehicleUseCase,
                SendVehicleToMaintenanceUseCase sendToMaintenanceUseCase,
                CompleteVehicleMaintenanceUseCase completeMaintenanceUseCase,
                RetireVehicleUseCase retireVehicleUseCase,
                UpdateVehicleOdometerUseCase updateOdometerUseCase,
                UpdateVehicleLocationUseCase updateLocationUseCase,
                FindBestVehicleForMissionUseCase findBestVehicleUseCase,
                CheckMaintenanceDueUseCase checkMaintenanceDueUseCase,
                ScheduleMaintenanceAlertUseCase scheduleMaintenanceAlertUseCase) {
            return new VehicleController(createVehicleUseCase, getVehicleUseCase, listVehiclesUseCase,
                    assignVehicleUseCase, unassignVehicleUseCase, sendToMaintenanceUseCase,
                    completeMaintenanceUseCase, retireVehicleUseCase, updateOdometerUseCase,
                    updateLocationUseCase, findBestVehicleUseCase, checkMaintenanceDueUseCase,
                    scheduleMaintenanceAlertUseCase);
        }
    }

    @Autowired
    private VehicleController controller;

    @Test
    void createVehicle_anonymousCaller_isDeniedBeforeReachingUseCase() {
        VehicleController.CreateVehicleRequest request = new VehicleController.CreateVehicleRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                "PLATE-1", "Brand", "Model", 2020, "VAN", 1000.0, 5.0, false);

        StepVerifier.create(controller.createVehicle(null, request))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }

    @Test
    void retireVehicle_anonymousCaller_isDeniedBeforeReachingUseCase() {
        StepVerifier.create(controller.retireVehicle(null, UUID.randomUUID()))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }
}
