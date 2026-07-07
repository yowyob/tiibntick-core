package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.out.AgencyRepositoryPort;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgencyService}.
 *
 * <p>Uses Mockito to mock all ports. No Spring context is loaded.
 * Reactive pipelines are verified with {@link StepVerifier}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgencyService — Application service tests")
class AgencyServiceTest {

    @Mock
    private AgencyRepositoryPort agencyRepositoryPort;

    @Mock
    private KernelOrganizationPort kernelOrganizationPort;

    private AgencyService agencyService;

    private static final UUID KERNEL_ORG_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agencyService = new AgencyService(agencyRepositoryPort, kernelOrganizationPort);
    }

    @Test
    @DisplayName("createAgency() should succeed when Kernel organization is active")
    void createAgency_shouldSucceedWhenKernelOrgIsActive() {
        // Given
        Agency expectedAgency = Agency.create(KERNEL_ORG_ID, TENANT_ID, "TiiBnTick Douala", null, null);
        when(kernelOrganizationPort.existsAndActive(KERNEL_ORG_ID)).thenReturn(Mono.just(true));
        when(agencyRepositoryPort.save(any(Agency.class))).thenReturn(Mono.just(expectedAgency));

        // When / Then
        StepVerifier.create(
                agencyService.createAgency(KERNEL_ORG_ID, TENANT_ID, "TiiBnTick Douala", null, null))
                .expectNextMatches(agency ->
                        agency.getOrganizationId().equals(KERNEL_ORG_ID) &&
                        agency.getTenantId().equals(TENANT_ID) &&
                        agency.getPrimaryCurrency().equals("XAF"))
                .verifyComplete();
    }

    @Test
    @DisplayName("createAgency() should emit error when Kernel organization is inactive or not found")
    void createAgency_shouldFailWhenKernelOrgNotFound() {
        // Given
        when(kernelOrganizationPort.existsAndActive(KERNEL_ORG_ID)).thenReturn(Mono.just(false));

        // When / Then
        StepVerifier.create(
                agencyService.createAgency(KERNEL_ORG_ID, TENANT_ID, "Agency X", null, null))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalArgumentException &&
                        ex.getMessage().contains(KERNEL_ORG_ID.toString()))
                .verify();
    }

    @Test
    @DisplayName("createAgency() should emit error when Kernel port returns empty Mono")
    void createAgency_shouldFailWhenKernelPortReturnsEmpty() {
        // Given — Kernel port returns empty (organization not found)
        when(kernelOrganizationPort.existsAndActive(KERNEL_ORG_ID)).thenReturn(Mono.just(false));

        // When / Then
        StepVerifier.create(
                agencyService.createAgency(KERNEL_ORG_ID, TENANT_ID, "Ghost Agency", null, null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("listAgenciesForTenant() should delegate to repository")
    void listAgenciesForTenant_shouldDelegateToRepository() {
        // Given
        Agency a1 = Agency.create(KERNEL_ORG_ID, TENANT_ID, "Agency 1", null, null);
        when(agencyRepositoryPort.findAllByTenantId(TENANT_ID))
                .thenReturn(reactor.core.publisher.Flux.just(a1));

        // When / Then
        StepVerifier.create(agencyService.listAgenciesForTenant(TENANT_ID))
                .expectNext(a1)
                .verifyComplete();
    }
}
