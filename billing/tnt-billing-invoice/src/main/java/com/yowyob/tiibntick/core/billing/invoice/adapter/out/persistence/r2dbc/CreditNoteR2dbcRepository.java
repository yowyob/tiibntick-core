package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.entity.CreditNoteEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for CreditNoteEntity.
 *
 * @author MANFOUO Braun
 */
public interface CreditNoteR2dbcRepository extends ReactiveCrudRepository<CreditNoteEntity, UUID> {

    @Query("SELECT * FROM tnt_credit_notes WHERE original_invoice_id = :originalInvoiceId ORDER BY issued_at DESC")
    Flux<CreditNoteEntity> findByOriginalInvoiceId(UUID originalInvoiceId);
}
