package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.dto.response.InvoiceResponse;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.mapper.InvoiceWebMapper;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.CancelInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.MarkInvoicePaidCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.service.InvoiceService;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.CreditNote;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WebFlux tests for {@link InvoiceController} — verifies the IDOR fix from
 * workstream-payment-billing-kernel-delegation step 7: the tenant used to
 * scope cancel/markPaid/issueCreditNote (and generate/listByClient) must come
 * from the authenticated JWT identity ({@code @CurrentUser}), never from a
 * client-controlled request.
 *
 * <p>Before the fix, these endpoints read {@code @RequestHeader("X-Tenant-Id")}
 * directly, so any caller could impersonate another tenant simply by setting
 * that header. These tests simulate an authenticated caller belonging to
 * {@code JWT_TENANT_ID} and assert that the command dispatched to the service
 * always carries that tenant — regardless of what the request otherwise implies.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceWebMapper mapper;

    private WebTestClient webTestClient;

    /** Tenant the authenticated caller actually belongs to, per the JWT. */
    private static final UUID JWT_TENANT_ID = UUID.randomUUID();

    /** A different tenant an attacker might try to reach — must never be used. */
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();

    private static final UUID INVOICE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        InvoiceController controller = new InvoiceController(invoiceService, mapper);
        webTestClient = WebTestClient
                .bindToController(controller)
                .controllerAdvice(new InvoiceExceptionHandler())
                .argumentResolvers(resolvers ->
                        resolvers.addCustomResolver(new FixedCurrentUserArgumentResolver(JWT_TENANT_ID)))
                .build();
    }

    /** Always resolves {@code @CurrentUser} to a fixed identity, ignoring anything in the request. */
    static class FixedCurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

        private final UUID tenantId;

        FixedCurrentUserArgumentResolver(UUID tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                             ServerWebExchange exchange) {
            return Mono.just(new TntUserIdentity(
                    UUID.randomUUID(), tenantId, UUID.randomUUID(),
                    null, null, Set.of("invoice:cancel", "payment:process", "invoice:issue"), false));
        }
    }

    /** All-null dummy response — only used so {@code .map(mapper::toResponse)} has a non-null value to emit. */
    private static InvoiceResponse dummyResponse() {
        return new InvoiceResponse(
                null,   // id
                null,   // number
                null,   // tenantId
                null,   // countryCode
                null,   // missionId
                null,   // salesOrderId
                null,   // clientId
                null,   // lines
                null,   // subtotalExTax
                null,   // taxLines
                null,   // totalTax
                null,   // totalIncTax
                null,   // discounts
                null,   // netAmount
                null,   // status
                null,   // pdfStorageKey
                null,   // issuedAt
                null,   // dueAt
                null,   // paidAt
                null,   // cancelledAt
                null,   // cancellationReason
                null,   // createdAt
                null,   // updatedAt
                null,   // issuerOrgType
                null,   // issuerOrgId
                null,   // issuerTradeName
                null,   // vatApplicable
                null,   // surchargeLines
                null,   // isFromTemplate
                null);  // appliedTemplateName
    }

    @Test
    @DisplayName("cancel() scopes the command to the JWT tenant, ignoring a spoofed X-Tenant-Id header")
    void cancelUsesJwtTenantNotHeader() {
        when(invoiceService.cancel(any())).thenReturn(Mono.just(mock(Invoice.class)));
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/billing/invoices/{invoiceId}/cancel")
                        .queryParam("reason", "test")
                        .build(INVOICE_ID))
                // Attacker-controlled header pointing at a foreign tenant — must be ignored entirely.
                .header("X-Tenant-Id", OTHER_TENANT_ID.toString())
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<CancelInvoiceCommand> captor = ArgumentCaptor.forClass(CancelInvoiceCommand.class);
        org.mockito.Mockito.verify(invoiceService).cancel(captor.capture());

        assertThat(captor.getValue().tenantId()).isEqualTo(JWT_TENANT_ID);
        assertThat(captor.getValue().tenantId()).isNotEqualTo(OTHER_TENANT_ID);
        assertThat(captor.getValue().invoiceId()).isEqualTo(INVOICE_ID);
    }

    @Test
    @DisplayName("markPaid() scopes the command to the JWT tenant, ignoring a spoofed X-Tenant-Id header")
    void markPaidUsesJwtTenantNotHeader() {
        when(invoiceService.markPaid(any())).thenReturn(Mono.just(mock(Invoice.class)));
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/billing/invoices/{invoiceId}/mark-paid")
                        .queryParam("paymentRef", "REF-1")
                        .build(INVOICE_ID))
                .header("X-Tenant-Id", OTHER_TENANT_ID.toString())
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<MarkInvoicePaidCommand> captor = ArgumentCaptor.forClass(MarkInvoicePaidCommand.class);
        org.mockito.Mockito.verify(invoiceService).markPaid(captor.capture());

        assertThat(captor.getValue().tenantId()).isEqualTo(JWT_TENANT_ID);
        assertThat(captor.getValue().tenantId()).isNotEqualTo(OTHER_TENANT_ID);
    }

    @Test
    @DisplayName("issueCreditNote() scopes the operation to the JWT tenant, ignoring a spoofed X-Tenant-Id header")
    void issueCreditNoteUsesJwtTenantNotHeader() {
        when(invoiceService.issueCreditNote(any(), any(), any()))
                .thenReturn(Mono.just(mock(CreditNote.class)));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/billing/invoices/{invoiceId}/credit-note")
                        .queryParam("reason", "goodwill")
                        .build(INVOICE_ID))
                .header("X-Tenant-Id", OTHER_TENANT_ID.toString())
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        org.mockito.Mockito.verify(invoiceService)
                .issueCreditNote(tenantCaptor.capture(), any(), any());

        assertThat(tenantCaptor.getValue()).isEqualTo(JWT_TENANT_ID);
        assertThat(tenantCaptor.getValue()).isNotEqualTo(OTHER_TENANT_ID);
    }
}
