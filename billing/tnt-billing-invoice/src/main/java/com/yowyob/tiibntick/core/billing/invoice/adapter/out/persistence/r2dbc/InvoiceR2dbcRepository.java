package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence.entity.InvoiceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for InvoiceEntity.
 *
 * @author MANFOUO Braun
 */
public interface InvoiceR2dbcRepository extends ReactiveCrudRepository<InvoiceEntity, UUID> {

    @Query("SELECT * FROM tnt_invoices WHERE id = :id AND tenant_id = :tenantId")
    Mono<InvoiceEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT * FROM tnt_invoices WHERE invoice_number = :number")
    Mono<InvoiceEntity> findByInvoiceNumber(String number);

    @Query("SELECT * FROM tnt_invoices WHERE mission_id = :missionId ORDER BY created_at DESC")
    Flux<InvoiceEntity> findByMissionId(String missionId);

    @Query("SELECT * FROM tnt_invoices WHERE tenant_id = :tenantId AND client_id = :clientId ORDER BY created_at DESC")
    Flux<InvoiceEntity> findByTenantIdAndClientId(UUID tenantId, String clientId);

    @Query("SELECT * FROM tnt_invoices WHERE tenant_id = :tenantId ORDER BY created_at DESC")
    Flux<InvoiceEntity> findByTenantId(UUID tenantId);

    @Query("SELECT * FROM tnt_invoices WHERE status IN ('ISSUED','PARTIALLY_PAID') AND due_at < :now")
    Flux<InvoiceEntity> findOverdue(LocalDateTime now);

    @Query("SELECT COUNT(*) > 0 FROM tnt_invoices WHERE mission_id = :missionId")
    Mono<Boolean> existsByMissionId(String missionId);
}
