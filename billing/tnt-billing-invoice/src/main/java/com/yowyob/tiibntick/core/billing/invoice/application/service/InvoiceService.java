package com.yowyob.tiibntick.core.billing.invoice.application.service;

import com.yowyob.tiibntick.core.billing.invoice.application.port.in.GenerateInvoiceUseCase;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.*;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.*;
import com.yowyob.tiibntick.core.billing.invoice.domain.exception.InvoiceNotFoundException;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;

/**
 * Application service orchestrating the full invoice lifecycle.
 *
 * <p>All write operations are protected by {@link RequirePermission} AOP guards
 * sourced from {@code tnt-roles-core}, ensuring that only actors with the appropriate
 * TiiBnTick RBAC permission can execute billing operations.</p>
 *
 * <p>Permission mapping:</p>
 * <ul>
 *   <li>{@code invoice:issue} — generate a new invoice</li>
 *   <li>{@code invoice:read} — read invoice data</li>
 *   <li>{@code invoice:cancel} — cancel an issued invoice</li>
 *   <li>{@code payment:process} — mark invoice as paid</li>
 *   <li>{@code report:read} — export PDF / get download URL</li>
 * </ul>
 *
 * <p>Use-case methods that persist the aggregate and then publish its domain events are
 * {@code @Transactional} so that the invoice row and the outbox envelope/entry written by
 * {@link InvoiceEventPublisher} (Chantier C · Audit n°3 · P5) commit atomically — a business
 * save can no longer succeed while its event is silently lost.
 *
 * @author MANFOUO Braun
 */
@Service
public class InvoiceService implements GenerateInvoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceEventPublisher eventPublisher;
    private final InvoicePdfPort pdfPort;
    private final InvoiceSequencePort sequencePort;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository,
            InvoiceEventPublisher eventPublisher,
            InvoicePdfPort pdfPort,
            InvoiceSequencePort sequencePort) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.eventPublisher = eventPublisher;
        this.pdfPort = pdfPort;
        this.sequencePort = sequencePort;
    }

    /**
     * Generates a new invoice from a billing command.
     *
     * <p>Sequence: generate invoice number → apply discount lines → issue → persist →
     * publish domain events → generate PDF → store PDF key.</p>
     *
     * <p>Requires permission {@code invoice:issue}.</p>
     *
     * @param command all fields needed to construct and issue the invoice
     * @return the persisted, issued Invoice with PDF storage key populated
     */
    @Override
    @Transactional
    @RequirePermission(resource = "invoice", action = "issue")
    public Mono<Invoice> generate(GenerateInvoiceCommand command) {
        int year = Year.now().getValue();
        return sequencePort.nextSequence(command.tenantId(), year)
                .flatMap(seq -> {
                    InvoiceNumber number = InvoiceNumber.generate(command.tenantCode(), year, seq);
                    Invoice draft = Invoice.createWithContext(
                            number, command.tenantId(), command.tenantCode(), command.countryCode(),
                            command.missionId(), command.salesOrderId(), command.clientId(),
                            command.lines(), command.discounts(), command.currency(),
                            command.issuerOrgType(), command.issuerOrgId(), command.issuerTradeName(),
                            command.vatApplicable(), command.surchargeLines(),
                            command.isFromTemplate(), command.appliedTemplateName());
                    Invoice issued = draft.issue(command.dueAt());
                    return invoiceRepository.save(issued)
                            .flatMap(saved -> {
                                var events = saved.collectAndClearEvents();
                                return eventPublisher.publishAll(events, saved.getTenantId())
                                        .then(pdfPort.generateAndStore(saved))
                                        .flatMap(pdfKey -> invoiceRepository.save(saved.withPdfStorageKey(pdfKey)))
                                        .doOnSuccess(inv -> log.info("Invoice generated: {}", inv.getNumber()))
                                        .onErrorResume(pdfErr -> {
                                            log.warn("PDF generation failed for {}: {}",
                                                    saved.getNumber(), pdfErr.getMessage());
                                            return Mono.just(saved);
                                        });
                            });
                });
    }

    /**
     * Retrieves an invoice by its UUID, scoped to the caller's tenant.
     * Requires permission {@code invoice:read}.
     *
     * @param invoiceId the invoice UUID
     * @param tenantId  the tenant the invoice must belong to
     * @return the matching Invoice or an error if not found (including cross-tenant access)
     */
    @RequirePermission(resource = "invoice", action = "read")
    public Mono<Invoice> getById(UUID invoiceId, UUID tenantId) {
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .switchIfEmpty(Mono.error(new InvoiceNotFoundException(invoiceId)));
    }

    /**
     * Retrieves an invoice by its business number (e.g. TNT-FACT-AGCY-2026-000042).
     * Requires permission {@code invoice:read}.
     *
     * @param invoiceNumber the formatted invoice number string
     * @return the matching Invoice or an error if not found
     */
    @RequirePermission(resource = "invoice", action = "read")
    public Mono<Invoice> getByNumber(String invoiceNumber) {
        return invoiceRepository.findByNumber(invoiceNumber)
                .switchIfEmpty(Mono.error(new InvoiceNotFoundException(invoiceNumber)));
    }

    /**
     * Lists all invoices for a client within a tenant.
     * Requires permission {@code invoice:read}.
     *
     * @param tenantId the tenant context
     * @param clientId the client whose invoices to list
     * @return a Flux of matching invoices
     */
    @RequirePermission(resource = "invoice", action = "read")
    public Flux<Invoice> listByClientId(UUID tenantId, String clientId) {
        return invoiceRepository.findByClientId(tenantId, clientId);
    }

    /**
     * Lists all invoices linked to a specific mission.
     * Requires permission {@code invoice:read}.
     *
     * @param missionId the mission ID
     * @return a Flux of matching invoices
     */
    @RequirePermission(resource = "invoice", action = "read")
    public Flux<Invoice> listByMissionId(String missionId) {
        return invoiceRepository.findByMissionId(missionId);
    }

    /**
     * Cancels an issued invoice and publishes a cancellation event.
     * Requires permission {@code invoice:cancel}.
     *
     * @param command cancel command with invoice ID and reason
     * @return the cancelled Invoice
     */
    @Transactional
    @RequirePermission(resource = "invoice", action = "cancel")
    public Mono<Invoice> cancel(CancelInvoiceCommand command) {
        return invoiceRepository.findByIdAndTenantId(command.invoiceId(), command.tenantId())
                .switchIfEmpty(Mono.error(new InvoiceNotFoundException(command.invoiceId())))
                .flatMap(invoice -> {
                    Invoice cancelled = invoice.cancel(command.reason());
                    return invoiceRepository.save(cancelled)
                            .flatMap(saved -> {
                                var events = saved.collectAndClearEvents();
                                return eventPublisher.publishAll(events, saved.getTenantId()).thenReturn(saved);
                            });
                });
    }

    /**
     * Marks an invoice as paid and records the payment reference.
     * Requires permission {@code payment:process}.
     *
     * @param command mark-paid command with invoice ID and payment reference
     * @return the updated Invoice in PAID status
     */
    @Transactional
    @RequirePermission(resource = "payment", action = "process")
    public Mono<Invoice> markPaid(MarkInvoicePaidCommand command) {
        return invoiceRepository.findByIdAndTenantId(command.invoiceId(), command.tenantId())
                .switchIfEmpty(Mono.error(new InvoiceNotFoundException(command.invoiceId())))
                .flatMap(invoice -> {
                    Invoice paid = invoice.markPaid(command.paymentRef());
                    return invoiceRepository.save(paid)
                            .flatMap(saved -> {
                                var events = saved.collectAndClearEvents();
                                return eventPublisher.publishAll(events, saved.getTenantId()).thenReturn(saved);
                            });
                });
    }

    /**
     * Returns a pre-signed download URL for the invoice PDF, scoped to the caller's tenant.
     * Requires permission {@code report:read}.
     *
     * @param invoiceId the invoice UUID
     * @param tenantId  the tenant the invoice must belong to
     * @return a Mono emitting the pre-signed URL (valid for 1 hour)
     */
    @RequirePermission(resource = "report", action = "read")
    public Mono<String> getPdfUrl(UUID invoiceId, UUID tenantId) {
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .switchIfEmpty(Mono.error(new InvoiceNotFoundException(invoiceId)))
                .flatMap(this::resolvePdfUrl);
    }

    private Mono<String> resolvePdfUrl(Invoice invoice) {
        if (invoice.getPdfStorageKey() != null) {
            return pdfPort.getDownloadUrl(invoice.getPdfStorageKey(), 3600);
        }
        return pdfPort.generateAndStore(invoice)
                .flatMap(key -> invoiceRepository.save(invoice.withPdfStorageKey(key))
                        .then(pdfPort.getDownloadUrl(key, 3600)));
    }

    /**
     * Scheduled operation — marks overdue invoices (past due date and unpaid).
     * This method is called from the scheduler and does not require a user permission check.
     *
     * @return the count of invoices marked overdue
     */
    public Mono<Long> markOverdueInvoices() {
        LocalDateTime now = LocalDateTime.now();
        return invoiceRepository.findOverdue(now)
                .flatMap(invoice -> invoiceRepository.save(invoice.markOverdue())
                        .doOnSuccess(inv -> log.info("Marked overdue: {}", inv.getNumber())))
                .count()
                .doOnSuccess(count -> log.info("Marked {} invoice(s) as overdue", count));
    }

    /**
     * Issues a credit note against a cancelled invoice.
     * Requires permission {@code invoice:issue}.
     *
     * @param tenantId  tenant context
     * @param invoiceId the invoice UUID to credit
     * @param reason    the reason for the credit note
     * @return the issued CreditNote
     */
    @RequirePermission(resource = "invoice", action = "issue")
    public Mono<CreditNote> issueCreditNote(UUID tenantId, UUID invoiceId, String reason) {
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .switchIfEmpty(Mono.error(new InvoiceNotFoundException(invoiceId)))
                .flatMap(invoice -> {
                    CreditNote note = CreditNote.issue(invoiceId, tenantId, invoice.getNetAmount(), reason);
                    return creditNoteRepository.save(note);
                });
    }
}
