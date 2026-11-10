package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
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
 * <h3>Security (IDOR fix — workstream-payment-billing-kernel-delegation, step 7;
 * completed under Audit n°7 · #5)</h3>
 * <p>Tenant identity is resolved from the JWT security context via
 * {@code @CurrentUser TntUserIdentity} instead of the client-supplied
 * {@code X-Tenant-Id} header — a caller could previously pass an arbitrary
 * tenant ID and operate on another tenant's invoices (cancel/markPaid/issueCreditNote,
 * plus generate/listByClient). {@code getById} and {@code getPdfUrl} now do the same
 * (previously the last two endpoints still calling {@code InvoiceService}'s deprecated,
 * unscoped overloads, which have since been removed). Permission enforcement is unchanged.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
@Tag(name = "TiiBnTick Invoices", description = "Invoice generation, lifecycle, and PDF management")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceWebMapper mapper;
    private final InvoiceAccessPolicy access;

    public InvoiceController(InvoiceService invoiceService, InvoiceWebMapper mapper, InvoiceAccessPolicy access) {
        this.invoiceService = invoiceService;
        this.mapper = mapper;
        this.access = access;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate and immediately issue an invoice for a delivery mission or sales order")
    public Mono<InvoiceResponse> generate(
            @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody GenerateInvoiceRequest request) {
        GenerateInvoiceCommand command = mapper.toCommand(currentUser.tenantId(), request);
        return invoiceService.generate(command).map(mapper::toResponse);
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get an invoice by its UUID")
    public Mono<InvoiceResponse> getById(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID invoiceId) {
        return invoiceService.getById(invoiceId, currentUser.tenantId())
                .flatMap(inv -> access.readable(inv, currentUser))
                .map(mapper::toResponse);
    }

    @GetMapping("/number/{invoiceNumber}")
    @Operation(summary = "Get an invoice by its formatted number (e.g., TNT-FACT-AGY001-2026-000042)")
    public Mono<InvoiceResponse> getByNumber(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable String invoiceNumber) {
        return invoiceService.getByNumber(invoiceNumber, currentUser.tenantId())
                .flatMap(inv -> access.readable(inv, currentUser))
                .map(mapper::toResponse);
    }

    @GetMapping("/mission/{missionId}")
    @Operation(summary = "List invoices for a specific delivery mission")
    public Flux<InvoiceResponse> listByMission(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable String missionId) {
        return access.readable(invoiceService.listByMissionId(missionId, currentUser.tenantId()), currentUser)
                .map(mapper::toResponse);
    }

    @GetMapping("/client/{clientId}")
    @Operation(summary = "List invoices for a client")
    public Flux<InvoiceResponse> listByClient(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable String clientId) {
        return access.readable(invoiceService.listByClientId(currentUser.tenantId(), clientId), currentUser)
                .map(mapper::toResponse);
    }

    @GetMapping("/mine")
    @Operation(summary = "Invoices addressed to the caller (as client), newest first")
    public Flux<InvoiceResponse> listMine(@CurrentUser TntUserIdentity currentUser) {
        return Flux.fromIterable(InvoiceAccessPolicy.idsOf(currentUser))
                .concatMap(id -> invoiceService.listByClientId(currentUser.tenantId(), id))
                .distinct(inv -> inv.getId())
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(mapper::toResponse);
    }

    @GetMapping("/issued/mine")
    @Operation(summary = "Invoices issued by the caller (a driver or organisation), newest first")
    public Flux<InvoiceResponse> listIssuedByMe(@CurrentUser TntUserIdentity currentUser) {
        return Flux.fromIterable(InvoiceAccessPolicy.idsOf(currentUser))
                .concatMap(id -> invoiceService.listByIssuerOrgId(currentUser.tenantId(), id))
                .distinct(inv -> inv.getId())
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(mapper::toResponse);
    }

    @PostMapping("/{invoiceId}/cancel")
    @Operation(summary = "Cancel an invoice (DRAFT or ISSUED only)")
    public Mono<InvoiceResponse> cancel(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID invoiceId,
            @RequestParam String reason) {
        CancelInvoiceCommand command = new CancelInvoiceCommand(currentUser.tenantId(), invoiceId, reason);
        return invoiceService.cancel(command).map(mapper::toResponse);
    }

    @PostMapping("/{invoiceId}/mark-paid")
    @Operation(summary = "Mark an invoice as paid (called by wallet/MoMo webhook)")
    public Mono<InvoiceResponse> markPaid(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID invoiceId,
            @RequestParam String paymentRef) {
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(currentUser.tenantId(), invoiceId, paymentRef);
        // Staff only: CLIENT holds payment:process (needed to pay), which must not let a client declare
        // an invoice paid by calling this endpoint — payments are confirmed by the wallet flow itself.
        return access.requireStaff()
                .then(Mono.defer(() -> invoiceService.markPaid(command)))
                .map(mapper::toResponse);
    }

    @GetMapping("/{invoiceId}/pdf")
    @Operation(summary = "Get a pre-signed download URL for the invoice PDF")
    public Mono<String> getPdfUrl(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID invoiceId) {
        return invoiceService.getById(invoiceId, currentUser.tenantId())
                .flatMap(inv -> access.readable(inv, currentUser))
                .then(Mono.defer(() -> invoiceService.getPdfUrl(invoiceId, currentUser.tenantId())));
    }

    @PostMapping("/{invoiceId}/credit-note")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a credit note for a paid invoice")
    public Mono<Void> issueCreditNote(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID invoiceId,
            @RequestParam String reason) {
        return invoiceService.issueCreditNote(currentUser.tenantId(), invoiceId, reason).then();
    }
}
