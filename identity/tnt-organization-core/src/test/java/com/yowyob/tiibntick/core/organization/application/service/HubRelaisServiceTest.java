package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.out.HubEventPublisherPort;
import com.yowyob.tiibntick.core.organization.application.port.out.HubRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.event.HubRelaisUpdatedEvent;
import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HubRelaisService}.
 *
 * <p>Uses Mockito to mock all ports. No Spring context is loaded.
 * Reactive pipelines are verified with {@link StepVerifier}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HubRelaisService — Application service tests")
class HubRelaisServiceTest {

    @Mock
    private HubRepositoryPort hubRepositoryPort;

    @Mock
    private KernelOrganizationPort kernelOrganizationPort;

    @Mock
    private HubEventPublisherPort hubEventPublisherPort;

    private HubRelaisService hubRelaisService;

    private static final UUID KERNEL_ORG_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String DOUALA_WKT = "POINT(9.7022 4.0511)";

    @BeforeEach
    void setUp() {
        hubRelaisService = new HubRelaisService(hubRepositoryPort, kernelOrganizationPort, hubEventPublisherPort);
        lenient().when(hubEventPublisherPort.publishHubUpdated(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("createHub() should succeed when Kernel organization is active")
    void createHub_shouldSucceedWhenKernelOrgIsActive() {
        // Given
        HubRelais expectedHub = HubRelais.create(
                KERNEL_ORG_ID, TENANT_ID, "Marché Central Hub", 50, DOUALA_WKT, "Mon-Sat 08-18", null);
        when(kernelOrganizationPort.existsAndActive(KERNEL_ORG_ID)).thenReturn(Mono.just(true));
        when(hubRepositoryPort.save(any(HubRelais.class))).thenReturn(Mono.just(expectedHub));

        // When / Then
        StepVerifier.create(hubRelaisService.createHub(
                KERNEL_ORG_ID, TENANT_ID, "Marché Central Hub", 50, DOUALA_WKT, "Mon-Sat 08-18", null))
                .expectNextMatches(hub ->
                        hub.getOrganizationId().equals(KERNEL_ORG_ID) &&
                        hub.getMaxParcelCapacity() == 50 &&
                        hub.isOperational())
                .verifyComplete();
    }

    @Test
    @DisplayName("createHub() should fail when Kernel organization is not found")
    void createHub_shouldFailWhenKernelOrgNotFound() {
        // Given
        when(kernelOrganizationPort.existsAndActive(KERNEL_ORG_ID)).thenReturn(Mono.just(false));

        // When / Then
        StepVerifier.create(hubRelaisService.createHub(
                KERNEL_ORG_ID, TENANT_ID, "Ghost Hub", 10, DOUALA_WKT, null, null))
                .expectErrorMatches(ex ->
                        ex instanceof ResponseStatusException rse &&
                        rse.getStatusCode() == HttpStatus.NOT_FOUND &&
                        rse.getReason() != null && rse.getReason().contains(KERNEL_ORG_ID.toString()))
                .verify();
    }

    @Test
    @DisplayName("checkHubCapacity() should return false when hub not found")
    void checkHubCapacity_shouldReturnFalseWhenHubNotFound() {
        // Given
        OrganizationId hubId = OrganizationId.generate();
        when(hubRepositoryPort.findById(hubId)).thenReturn(Mono.empty());

        // When / Then
        StepVerifier.create(hubRelaisService.checkHubCapacity(hubId, 5))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("checkHubCapacity() should return true when occupancy is below capacity")
    void checkHubCapacity_shouldReturnTrueWhenCapacityAvailable() {
        // Given
        OrganizationId hubId = OrganizationId.generate();
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 20, DOUALA_WKT, null, null);
        when(hubRepositoryPort.findById(hubId)).thenReturn(Mono.just(hub));

        // When / Then
        StepVerifier.create(hubRelaisService.checkHubCapacity(hubId, 15))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("checkHubCapacity() should return false when hub is full")
    void checkHubCapacity_shouldReturnFalseWhenFull() {
        // Given
        OrganizationId hubId = OrganizationId.generate();
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);
        when(hubRepositoryPort.findById(hubId)).thenReturn(Mono.just(hub));

        // When / Then
        StepVerifier.create(hubRelaisService.checkHubCapacity(hubId, 10))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("updateCapacity() persists the new capacity and publishes HubRelaisUpdatedEvent")
    void updateCapacity_shouldPersistAndPublish() {
        OrganizationId hubId = OrganizationId.generate();
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);
        when(hubRepositoryPort.findById(hubId)).thenReturn(Mono.just(hub));
        when(hubRepositoryPort.save(any(HubRelais.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(hubRelaisService.updateCapacity(hubId, 99))
                .expectNextMatches(saved -> saved.getMaxParcelCapacity() == 99)
                .verifyComplete();

        ArgumentCaptor<HubRelaisUpdatedEvent> captor = ArgumentCaptor.forClass(HubRelaisUpdatedEvent.class);
        verify(hubEventPublisherPort).publishHubUpdated(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().updateReason()).isEqualTo("CAPACITY_UPDATED");
    }

    @Test
    @DisplayName("assignOperator() persists the new operator and publishes HubRelaisUpdatedEvent")
    void assignOperator_shouldPersistAndPublish() {
        OrganizationId hubId = OrganizationId.generate();
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);
        UUID newOperatorId = UUID.randomUUID();
        when(hubRepositoryPort.findById(hubId)).thenReturn(Mono.just(hub));
        when(hubRepositoryPort.save(any(HubRelais.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(hubRelaisService.assignOperator(hubId, newOperatorId))
                .expectNextMatches(saved -> newOperatorId.equals(saved.getOperatorId()))
                .verifyComplete();

        ArgumentCaptor<HubRelaisUpdatedEvent> captor = ArgumentCaptor.forClass(HubRelaisUpdatedEvent.class);
        verify(hubEventPublisherPort).publishHubUpdated(captor.capture());
        assertThat(captor.getValue().updateReason()).isEqualTo("OPERATOR_ASSIGNED");
    }

    @Test
    @DisplayName("suspendHub() then resumeHub() toggle operational status and each publish an event")
    void suspendThenResumeHub_shouldToggleOperationalStatusAndPublish() {
        OrganizationId hubId = OrganizationId.generate();
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);
        when(hubRepositoryPort.findById(hubId)).thenReturn(Mono.just(hub));
        when(hubRepositoryPort.save(any(HubRelais.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(hubRelaisService.suspendHub(hubId))
                .expectNextMatches(saved -> !saved.isOperational())
                .verifyComplete();

        StepVerifier.create(hubRelaisService.resumeHub(hubId))
                .expectNextMatches(HubRelais::isOperational)
                .verifyComplete();

        ArgumentCaptor<HubRelaisUpdatedEvent> captor = ArgumentCaptor.forClass(HubRelaisUpdatedEvent.class);
        verify(hubEventPublisherPort, org.mockito.Mockito.times(2)).publishHubUpdated(captor.capture());
        assertThat(captor.getAllValues()).extracting(HubRelaisUpdatedEvent::updateReason)
                .containsExactly("SUSPENDED", "RESUMED");
    }
}
