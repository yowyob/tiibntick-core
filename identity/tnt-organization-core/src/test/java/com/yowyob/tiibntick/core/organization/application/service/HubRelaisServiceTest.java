package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.out.HubRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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

    private HubRelaisService hubRelaisService;

    private static final UUID KERNEL_ORG_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String DOUALA_WKT = "POINT(9.7022 4.0511)";

    @BeforeEach
    void setUp() {
        hubRelaisService = new HubRelaisService(hubRepositoryPort, kernelOrganizationPort);
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
}
