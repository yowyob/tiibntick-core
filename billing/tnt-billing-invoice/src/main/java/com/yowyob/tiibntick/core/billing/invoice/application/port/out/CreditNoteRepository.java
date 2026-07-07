package com.yowyob.tiibntick.core.billing.invoice.application.port.out;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.CreditNote;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: persistence for CreditNote.
 *
 * @author MANFOUO Braun
 */
public interface CreditNoteRepository {
    Mono<CreditNote> save(CreditNote creditNote);
    Mono<CreditNote> findById(UUID id);
    Flux<CreditNote> findByOriginalInvoiceId(UUID originalInvoiceId);
}
