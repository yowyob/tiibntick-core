package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #4 — {@link DisputeController} used to read the tenant
 * straight off the client-supplied {@code X-Tenant-ID} header, so any authenticated caller
 * could open/read another tenant's disputes simply by changing that header. The fix
 * resolves the tenant from {@code @CurrentUser TntUserIdentity} instead (same pattern as
 * {@code ReportingController}, {@code DeliveryController}, etc. — see
 * {@code ReportingControllerTenantHeaderIgnoredTest} in tnt-billing-report for the
 * original regression test this one mirrors).
 *
 * <p>This test proves the header is now inert: even when the request carries
 * {@code X-Tenant-ID} for a different ("attacker") tenant, the command passed to
 * {@link IDisputeCommandUseCase} still carries the tenant resolved from the authenticated
 * identity, not the header value.
 *
 * @author MANFOUO Braun
 */
class DisputeControllerTenantHeaderIgnoredTest {

    @Test
    void openDispute_ignoresSpoofedTenantHeader_usesResolvedIdentityTenant() {
        UUID resolvedTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(resolvedTenant).isNotEqualTo(attackerSuppliedTenantHeader);

        IDisputeCommandUseCase commandUseCase = mock(IDisputeCommandUseCase.class);
        IDisputeQueryUseCase queryUseCase = mock(IDisputeQueryUseCase.class);
        DisputeController controller = new DisputeController(commandUseCase, queryUseCase);

        // Command construction only needs to be observed — the mapper's response
        // translation is irrelevant to the tenant-resolution property under test, so the
        // use case errors immediately (before any mapping happens) once the argument is
        // captured.
        when(commandUseCase.openDispute(any())).thenReturn(Mono.error(new RuntimeException("stop-test")));

        ResolveCurrentUserUseCase resolveCurrentUserUseCase = mock(ResolveCurrentUserUseCase.class);
        TntSecurityContext resolvedContext = TntSecurityContext.builder()
                .userId(UUID.randomUUID())
                .tenantId(resolvedTenant)
                .authenticated(true)
                .build();
        when(resolveCurrentUserUseCase.resolveCurrentIdentity())
                .thenReturn(Mono.just(TntUserIdentity.from(resolvedContext)));

        WebTestClient webClient = WebTestClient.bindToController(controller)
                .argumentResolvers(configurer -> configurer.addCustomResolver(
                        new TntCurrentUserArgumentResolver(resolveCurrentUserUseCase)))
                .build();

        DisputeRequests.OpenDisputeRequest request = new DisputeRequests.OpenDisputeRequest(
                "PACKAGE_DAMAGED", "MISSION_AGENCY", "NORMAL", "claimant-1", "CLIENT",
                "respondent-1", "AGENCY", "mission-1", "package-1", "TRK-1",
                "Parcel arrived damaged", null, null, false);

        webClient.post()
                .uri("/api/v1/disputes")
                // The spoofed header a malicious/careless caller would send — must be ignored.
                .header("X-Tenant-ID", attackerSuppliedTenantHeader.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError(); // stop-test error surfaces as 500 — irrelevant to what we assert below

        ArgumentCaptor<OpenDisputeCommand> captor = ArgumentCaptor.forClass(OpenDisputeCommand.class);
        verify(commandUseCase).openDispute(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the resolved identity, never from the client-supplied X-Tenant-ID header")
                .isEqualTo(resolvedTenant.toString())
                .isNotEqualTo(attackerSuppliedTenantHeader.toString());
    }
}
