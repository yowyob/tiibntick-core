package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.dto.request.GenerateInvoiceRequest;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.dto.response.InvoiceResponse;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.mapper.InvoiceWebMapper;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.CancelInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.MarkInvoicePaidCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for Invoice management.
 * Base path: /api/v1/billing/invoices
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
@Tag(name = "TiiBnTick Invoices", description = "Invoice generation, lifecycle, and PDF management")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceWebMapper mapper;

    public InvoiceController(InvoiceService invoiceService, InvoiceWebMapper mapper) {
        this.invoiceService = invoiceService;
        this.mapper = mapper;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate and immediately issue an invoice for a delivery mission or sales order")
    public Mono<InvoiceResponse> generate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody GenerateInvoiceRequest request) {
        GenerateInvoiceCommand command = mapper.toCommand(tenantId, request);
        return invoiceService.generate(command).map(mapper::toResponse);
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get an invoice by its UUID")
    public Mono<InvoiceResponse> getById(@PathVariable UUID invoiceId) {
        return invoiceService.getById(invoiceId).map(mapper::toResponse);
    }

    @GetMapping("/number/{invoiceNumber}")
    @Operation(summary = "Get an invoice by its formatted number (e.g., TNT-FACT-AGY001-2026-000042)")
    public Mono<InvoiceResponse> getByNumber(@PathVariable String invoiceNumber) {
        return invoiceService.getByNumber(invoiceNumber).map(mapper::toResponse);
    }

    @GetMapping("/mission/{missionId}")
    @Operation(summary = "List invoices for a specific delivery mission")
    public Flux<InvoiceResponse> listByMission(@PathVariable String missionId) {
        return invoiceService.listByMissionId(missionId).map(mapper::toResponse);
    }

    @GetMapping("/client/{clientId}")
    @Operation(summary = "List invoices for a client")
    public Flux<InvoiceResponse> listByClient(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String clientId) {
        return invoiceService.listByClientId(tenantId, clientId).map(mapper::toResponse);
    }

    @PostMapping("/{invoiceId}/cancel")
    @Operation(summary = "Cancel an invoice (DRAFT or ISSUED only)")
    public Mono<InvoiceResponse> cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID invoiceId,
            @RequestParam String reason) {
        CancelInvoiceCommand command = new CancelInvoiceCommand(tenantId, invoiceId, reason);
        return invoiceService.cancel(command).map(mapper::toResponse);
    }

    @PostMapping("/{invoiceId}/mark-paid")
    @Operation(summary = "Mark an invoice as paid (called by wallet/MoMo webhook)")
    public Mono<InvoiceResponse> markPaid(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID invoiceId,
            @RequestParam String paymentRef) {
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(tenantId, invoiceId, paymentRef);
        return invoiceService.markPaid(command).map(mapper::toResponse);
    }

    @GetMapping("/{invoiceId}/pdf")
    @Operation(summary = "Get a pre-signed download URL for the invoice PDF")
    public Mono<String> getPdfUrl(@PathVariable UUID invoiceId) {
        return invoiceService.getPdfUrl(invoiceId);
    }

    @PostMapping("/{invoiceId}/credit-note")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a credit note for a paid invoice")
    public Mono<Void> issueCreditNote(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID invoiceId,
            @RequestParam String reason) {
        return invoiceService.issueCreditNote(tenantId, invoiceId, reason).then();
    }
}
