package com.yowyob.tiibntick.core.accounting.application.port.out;

import com.yowyob.tiibntick.core.accounting.domain.model.KernelInvoiceDto;
import com.yowyob.tiibntick.core.accounting.domain.model.KernelJournalEntryDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel Accounting Bridge.
 *
 * <p>Defines the contract for querying the Yowyob Kernel (RT-comops-accounting-core)
 * about invoices and journal entries. TiiBnTick does NOT inherit or extend Kernel
 * accounting classes. Instead, it references Kernel entities by their UUID
 * ({@code kernelInvoiceId}, {@code kernelJournalEntryId}) and only accesses the
 * Kernel for optional validation or enrichment.</p>
 *
 * <p>The coupling is intentionally <em>optional</em>: a TiiBnTick JournalEntry can
 * exist with {@code null} Kernel references for entries generated from informal
 * transactions not registered in the Kernel ERP.</p>
 *
 * <p>Implementation: {@link com.yowyob.tiibntick.core.accounting.adapter.out.kernel.KernelAccountingAdapter}
 * (reactive WebClient over the Kernel REST API).</p>
 *
 * @author MANFOUO Braun
 */
public interface KernelAccountingPort {

    /**
     * Fetches a Kernel invoice by its UUID.
     *
     * <p>Returns {@code Mono.empty()} if the invoice does not exist in the Kernel,
     * allowing callers to treat the reference as optional.</p>
     *
     * @param kernelInvoiceId the Kernel invoice UUID (typically the billing event referenceId)
     * @return the kernel invoice data, or {@code Mono.empty()} if not found
     */
    Mono<KernelInvoiceDto> findInvoiceById(UUID kernelInvoiceId);

    /**
     * Fetches a Kernel journal entry by its UUID.
     *
     * <p>Returns {@code Mono.empty()} if the entry does not exist in the Kernel.</p>
     *
     * @param kernelJournalEntryId the Kernel journal entry UUID
     * @return the kernel journal entry data, or {@code Mono.empty()} if not found
     */
    Mono<KernelJournalEntryDto> findJournalEntryById(UUID kernelJournalEntryId);

    /**
     * Resolves a Kernel invoice by the billing reference ID (as received in Kafka events).
     *
     * <p>Used when auto-generating journal entries from billing events to optionally
     * link the TNT journal entry to its Kernel invoice counterpart.</p>
     *
     * @param tenantId    tenant context
     * @param referenceId the reference ID from the billing event (e.g. invoice number, payment ID)
     * @return the matching Kernel invoice, or {@code Mono.empty()} if none found
     */
    Mono<KernelInvoiceDto> findInvoiceByReferenceId(UUID tenantId, String referenceId);
}
