package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.mapper.InvoicePersistenceMapper;
import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.r2dbc.CreditNoteR2dbcRepository;
import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.r2dbc.InvoiceR2dbcRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.CreditNoteRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceRepository;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.CreditNote;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adapter: implements InvoiceRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final InvoiceR2dbcRepository r2dbcRepo;
    private final InvoicePersistenceMapper mapper;

    public InvoiceRepositoryAdapter(InvoiceR2dbcRepository r2dbcRepo, InvoicePersistenceMapper mapper) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<Invoice> save(Invoice invoice) {
        var entity = mapper.toEntity(invoice);
        return r2dbcRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Invoice> findById(UUID invoiceId) {
        return r2dbcRepo.findById(invoiceId).map(mapper::toDomain);
    }

    @Override
    public Mono<Invoice> findByIdAndTenantId(UUID invoiceId, UUID tenantId) {
        return r2dbcRepo.findByIdAndTenantId(invoiceId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<Invoice> findByNumber(String invoiceNumber) {
        return r2dbcRepo.findByInvoiceNumber(invoiceNumber).map(mapper::toDomain);
    }

    @Override
    public Flux<Invoice> findByMissionId(String missionId) {
        return r2dbcRepo.findByMissionId(missionId).map(mapper::toDomain);
    }

    @Override
    public Flux<Invoice> findByClientId(UUID tenantId, String clientId) {
        return r2dbcRepo.findByTenantIdAndClientId(tenantId, clientId).map(mapper::toDomain);
    }

    @Override
    public Flux<Invoice> findByTenantId(UUID tenantId) {
        return r2dbcRepo.findByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<Invoice> findOverdue(LocalDateTime now) {
        return r2dbcRepo.findOverdue(now).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByMissionId(String missionId) {
        return r2dbcRepo.existsByMissionId(missionId);
    }
}

/**
 * Adapter: implements CreditNoteRepository using Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
class CreditNoteRepositoryAdapter implements CreditNoteRepository {

    private final CreditNoteR2dbcRepository r2dbcRepo;
    private final InvoicePersistenceMapper mapper;

    CreditNoteRepositoryAdapter(CreditNoteR2dbcRepository r2dbcRepo, InvoicePersistenceMapper mapper) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<CreditNote> save(CreditNote creditNote) {
        return r2dbcRepo.existsById(creditNote.getId())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(creditNote);
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<CreditNote> findById(UUID id) {
        return r2dbcRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Flux<CreditNote> findByOriginalInvoiceId(UUID originalInvoiceId) {
        return r2dbcRepo.findByOriginalInvoiceId(originalInvoiceId).map(mapper::toDomain);
    }
}
