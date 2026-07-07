package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.JournalEntryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for JournalEntry entities.
 * Author: MANFOUO Braun
 */
public interface JournalEntryR2dbcRepository extends ReactiveCrudRepository<JournalEntryEntity, UUID> {

    @Query("SELECT * FROM accounting.journal_entries WHERE tenant_id = :tenantId AND id = :id")
    Mono<JournalEntryEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            SELECT * FROM accounting.journal_entries
            WHERE tenant_id = :tenantId
              AND organization_id = :organizationId
              AND EXTRACT(YEAR FROM created_at) = :year
              AND EXTRACT(MONTH FROM created_at) = :month
            ORDER BY created_at DESC
            """)
    Flux<JournalEntryEntity> findByTenantIdAndOrganizationIdAndPeriod(
            UUID tenantId, UUID organizationId, int year, int month);

    @Query("SELECT * FROM accounting.journal_entries WHERE tenant_id = :tenantId AND reference_id = :referenceId")
    Flux<JournalEntryEntity> findByTenantIdAndReferenceId(UUID tenantId, String referenceId);

    @Query("SELECT COUNT(*) > 0 FROM accounting.journal_entries WHERE tenant_id = :tenantId AND number = :number")
    Mono<Boolean> existsByTenantIdAndNumber(UUID tenantId, String number);
}
