package com.yowyob.tiibntick.core.billing.report.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response.RevenueReportResponse;
import com.yowyob.tiibntick.core.billing.report.adapter.in.web.mapper.ReportWebMapper;
import com.yowyob.tiibntick.core.billing.report.application.port.in.query.RevenueReportQuery;
import com.yowyob.tiibntick.core.billing.report.application.service.ReportingService;
import com.yowyob.tiibntick.core.billing.report.domain.model.RevenueReport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #4 — ReportingController used to read the tenant
 * straight off the client-supplied {@code X-Tenant-Id} header, so any caller could read
 * another tenant's revenue report simply by changing that header. The fix resolves the
 * tenant from the JWT via {@code @CurrentUser TntUserIdentity} instead (same pattern
 * already used by {@code DeliveryController}, {@code MarketOrderController}, etc.).
 *
 * <p>This test proves the header is now inert: even when the request carries
 * {@code X-Tenant-Id} for a different ("attacker") tenant, the query passed to
 * {@link ReportingService} still carries the tenant resolved from the authenticated
 * JWT context, not the header value.
 *
 * @author MANFOUO Braun
 */
class ReportingControllerTenantHeaderIgnoredTest {

    @Test
    void getRevenueReport_ignoresSpoofedTenantHeader_usesJwtTenant() {
        UUID jwtTenant = UUID.randomUUID();
        UUID attackerSuppliedTenantHeader = UUID.randomUUID();
        assertThat(jwtTenant).isNotEqualTo(attackerSuppliedTenantHeader);

        ReportingService reportingService = mock(ReportingService.class);
        ReportWebMapper mapper = mock(ReportWebMapper.class);
        ReportingController controller = new ReportingController(reportingService, mapper);

        RevenueReport stub = mock(RevenueReport.class);
        when(reportingService.generateRevenueReport(any())).thenReturn(Mono.just(stub));
        RevenueReportResponse responseStub = new RevenueReportResponse(
                UUID.randomUUID(), UUID.randomUUID(), null, 0, 0, 0, 0,
                null, null, null, null, null, null, 0.0, List.of(), Instant.now());
        when(mapper.toResponse(stub)).thenReturn(responseStub);

        ResolveCurrentUserUseCase resolveCurrentUserUseCase = mock(ResolveCurrentUserUseCase.class);
        TntSecurityContext jwtContext = TntSecurityContext.builder()
                .userId(UUID.randomUUID())
                .tenantId(jwtTenant)
                .authenticated(true)
                .build();
        when(resolveCurrentUserUseCase.resolveCurrentIdentity())
                .thenReturn(Mono.just(TntUserIdentity.from(jwtContext)));

        WebTestClient webClient = WebTestClient.bindToController(controller)
                .argumentResolvers(configurer -> configurer.addCustomResolver(
                        new TntCurrentUserArgumentResolver(resolveCurrentUserUseCase)))
                .build();

        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/billing/reports/revenue")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-01-31")
                        .build())
                // The spoofed header a malicious/careless client would send — must be ignored.
                .header("X-Tenant-Id", attackerSuppliedTenantHeader.toString())
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<RevenueReportQuery> captor = ArgumentCaptor.forClass(RevenueReportQuery.class);
        org.mockito.Mockito.verify(reportingService).generateRevenueReport(captor.capture());
        assertThat(captor.getValue().tenantId())
                .as("tenant must come from the JWT, never from the client-supplied X-Tenant-Id header")
                .isEqualTo(jwtTenant)
                .isNotEqualTo(attackerSuppliedTenantHeader);
    }
}
