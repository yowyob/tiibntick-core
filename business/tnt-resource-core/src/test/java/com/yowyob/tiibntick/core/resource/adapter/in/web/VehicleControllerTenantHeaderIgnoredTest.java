package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.resource.application.port.in.AssignVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.AssignVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.CheckMaintenanceDueUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.CompleteVehicleMaintenanceUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.CreateVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.CreateVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.FindBestVehicleForMissionUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.GetVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.ListVehiclesByAgencyUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.RetireVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.ScheduleMaintenanceAlertUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.SendVehicleToMaintenanceUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.UnassignVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.UpdateVehicleLocationUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.UpdateVehicleOdometerUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test — {@link VehicleController} used to resolve {@code tenantId} straight off
 * client-supplied input (a {@code tenantId} query parameter on GET endpoints, or a
 * {@code tenantId} field in the mutation request bodies), so any authenticated caller could
 * read/write another tenant's vehicles simply by passing a different {@code tenantId}. The fix
 * resolves the tenant from {@code @CurrentUser TntUserIdentity} instead (same pattern as the
 * sibling {@code EquipmentController}).
 *
 * <p>This test proves client-supplied tenant input is now inert: even when the request carries
 * a spoofed {@code tenantId} for a different ("attacker") tenant, the command passed to the
 * use case still carries the tenant resolved from the authenticated identity, never the
 * client-supplied value.
 *
 * @author MANFOUO Braun
 */
class VehicleControllerTenantHeaderIgnoredTest {

    private final CreateVehicleUseCase createVehicleUseCase = mock(CreateVehicleUseCase.class);
    private final GetVehicleUseCase getVehicleUseCase = mock(GetVehicleUseCase.class);
    private final ListVehiclesByAgencyUseCase listVehiclesUseCase = mock(ListVehiclesByAgencyUseCase.class);
    private final AssignVehicleUseCase assignVehicleUseCase = mock(AssignVehicleUseCase.class);
    private final UnassignVehicleUseCase unassignVehicleUseCase = mock(UnassignVehicleUseCase.class);
    private final SendVehicleToMaintenanceUseCase sendToMaintenanceUseCase = mock(SendVehicleToMaintenanceUseCase.class);
    private final CompleteVehicleMaintenanceUseCase completeMaintenanceUseCase = mock(CompleteVehicleMaintenanceUseCase.class);
    private final RetireVehicleUseCase retireVehicleUseCase = mock(RetireVehicleUseCase.class);
    private final UpdateVehicleOdometerUseCase updateOdometerUseCase = mock(UpdateVehicleOdometerUseCase.class);
    private final UpdateVehicleLocationUseCase updateLocationUseCase = mock(UpdateVehicleLocationUseCase.class);
    private final FindBestVehicleForMissionUseCase findBestVehicleUseCase = mock(FindBestVehicleForMissionUseCase.class);
    private final CheckMaintenanceDueUseCase checkMaintenanceDueUseCase = mock(CheckMaintenanceDueUseCase.class);
    private final ScheduleMaintenanceAlertUseCase scheduleMaintenanceAlertUseCase = mock(ScheduleMaintenanceAlertUseCase.class);

    private final VehicleController controller = new VehicleController(
            createVehicleUseCase, getVehicleUseCase, listVehiclesUseCase,
            assignVehicleUseCase, unassignVehicleUseCase, sendToMaintenanceUseCase,
            completeMaintenanceUseCase, retireVehicleUseCase, updateOdometerUseCase,
            updateLocationUseCase, findBestVehicleUseCase, checkMaintenanceDueUseCase,
            scheduleMaintenanceAlertUseCase);

    /** Builds a {@link WebTestClient} resolving {@code @CurrentUser} to {@code resolvedTenant}. */
    private WebTestClient webClientFor(UUID resolvedTenant) {
        ResolveCurrentUserUseCase resolveCurrentUserUseCase = mock(ResolveCurrentUserUseCase.class);
        TntSecurityContext resolvedContext = TntSecurityContext.builder()
                .userId(UUID.randomUUID())
                .tenantId(resolvedTenant)
                .authenticated(true)
                .build();
        when(resolveCurrentUserUseCase.resolveCurrentIdentity())
                .thenReturn(Mono.just(TntUserIdentity.from(resolvedContext)));

        return WebTestClient.bindToController(controller)
                .argumentResolvers(configurer -> configurer.addCustomResolver(
                        new TntCurrentUserArgumentResolver(resolveCurrentUserUseCase)))
                .build();
    }

    @Test
    void createVehicle_ignoresSpoofedTenantInBody_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenant = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenant);

        when(createVehicleUseCase.createVehicle(any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        String body = """
                {"tenantId":"%s","organizationId":"%s","agencyId":"%s",
                 "registrationNumber":"PLATE-1","brand":"Brand","model":"Model",
                 "yearOfManufacture":2020,"type":"VAN","maxWeightKg":1000.0,
                 "maxVolumeM3":5.0,"hasRefrigeration":false}
                """.formatted(attackerSuppliedTenant, UUID.randomUUID(), UUID.randomUUID());

        webClientFor(resolvedTenant).post()
                .uri("/api/resources/vehicles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().is5xxServerError(); // stop-test error surfaces as 500 — irrelevant to what we assert below

        ArgumentCaptor<CreateVehicleCommand> captor = ArgumentCaptor.forClass(CreateVehicleCommand.class);
        verify(createVehicleUseCase).createVehicle(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from a client-supplied tenantId field")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenant);
    }

    @Test
    void getVehicle_ignoresSpoofedTenantQueryParam_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenant = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenant);

        when(getVehicleUseCase.getVehicle(any(), any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        webClientFor(resolvedTenant).get()
                .uri(uriBuilder -> uriBuilder.path("/api/resources/vehicles/{vehicleId}")
                        .queryParam("tenantId", attackerSuppliedTenant)
                        .build(vehicleId))
                .exchange()
                .expectStatus().is5xxServerError();

        verify(getVehicleUseCase).getVehicle(resolvedTenant, vehicleId);
    }

    @Test
    void assignVehicle_ignoresSpoofedTenantInBody_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenant = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenant);

        when(assignVehicleUseCase.assignVehicle(any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        String body = """
                {"tenantId":"%s","delivererId":"%s","missionId":"%s"}
                """.formatted(attackerSuppliedTenant, UUID.randomUUID(), UUID.randomUUID());

        webClientFor(resolvedTenant).post()
                .uri("/api/resources/vehicles/{vehicleId}/assign", vehicleId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().is5xxServerError();

        ArgumentCaptor<AssignVehicleCommand> captor = ArgumentCaptor.forClass(AssignVehicleCommand.class);
        verify(assignVehicleUseCase).assignVehicle(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from a client-supplied tenantId field")
                .isEqualTo(resolvedTenant)
                .isNotEqualTo(attackerSuppliedTenant);
    }

    @Test
    void unassignVehicle_ignoresSpoofedTenantQueryParam_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenant = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenant);

        when(unassignVehicleUseCase.unassignVehicle(any(), any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        webClientFor(resolvedTenant).post()
                .uri(uriBuilder -> uriBuilder.path("/api/resources/vehicles/{vehicleId}/unassign")
                        .queryParam("tenantId", attackerSuppliedTenant)
                        .build(vehicleId))
                .exchange()
                .expectStatus().is5xxServerError();

        verify(unassignVehicleUseCase).unassignVehicle(resolvedTenant, vehicleId);
    }

    @Test
    void retireVehicle_ignoresSpoofedTenantQueryParam_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenant = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenant);

        when(retireVehicleUseCase.retireVehicle(any(), any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        webClientFor(resolvedTenant).post()
                .uri(uriBuilder -> uriBuilder.path("/api/resources/vehicles/{vehicleId}/retire")
                        .queryParam("tenantId", attackerSuppliedTenant)
                        .build(vehicleId))
                .exchange()
                .expectStatus().is5xxServerError();

        verify(retireVehicleUseCase).retireVehicle(resolvedTenant, vehicleId);
    }
}
