package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.JournalEntryEntity;
import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.JournalEntryLineEntity;
import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository.JournalEntryLineR2dbcRepository;
import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository.JournalEntryR2dbcRepository;
import com.yowyob.tiibntick.core.accounting.application.port.out.JournalEntryRepository;
import com.yowyob.tiibntick.core.accounting.domain.model.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

/**
 * R2DBC adapter implementing {@link JournalEntryRepository} port.
 *
 * <p>Handles the JournalEntry aggregate (header + lines) across two tables.
 * Both {@code kernelInvoiceId} and {@code kernelJournalEntryId} are transparently
 * persisted and rehydrated as nullable Kernel integration references.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class JournalEntryRepositoryAdapter implements JournalEntryRepository {

    private final JournalEntryR2dbcRepository headerRepository;
    private final JournalEntryLineR2dbcRepository lineRepository;
    private final R2dbcEntityTemplate entityTemplate;

    public JournalEntryRepositoryAdapter(JournalEntryR2dbcRepository headerRepository,
                                          JournalEntryLineR2dbcRepository lineRepository,
                                          R2dbcEntityTemplate entityTemplate) {
        this.headerRepository = headerRepository;
        this.lineRepository = lineRepository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<JournalEntry> save(JournalEntry entry) {
        JournalEntryEntity headerEntity = toHeaderEntity(entry);
        return headerRepository.existsById(headerEntity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(headerEntity)
                        : entityTemplate.insert(headerEntity))
                .flatMap(saved -> lineRepository.deleteByJournalEntryId(saved.id())
                        .thenMany(Flux.fromIterable(entry.getLines())
                                .map(line -> toLineEntity(saved.id(), line))
                                .flatMap(entityTemplate::insert))
                        .then(Mono.just(saved)))
                .flatMap(this::toDomain);
    }

    @Override
    public Mono<JournalEntry> findById(UUID tenantId, UUID journalEntryId) {
        return headerRepository.findByTenantIdAndId(tenantId, journalEntryId)
                .flatMap(this::toDomain);
    }

    @Override
    public Flux<JournalEntry> findByTenantIdAndOrganizationIdAndPeriod(UUID tenantId,
                                                                         UUID organizationId,
                                                                         YearMonth period) {
        return headerRepository.findByTenantIdAndOrganizationIdAndPeriod(
                        tenantId, organizationId, period.getYear(), period.getMonthValue())
                .flatMap(this::toDomain);
    }

    @Override
    public Flux<JournalEntry> findByReferenceId(UUID tenantId, String referenceId) {
        return headerRepository.findByTenantIdAndReferenceId(tenantId, referenceId)
                .flatMap(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByNumber(UUID tenantId, String journalNumber) {
        return headerRepository.existsByTenantIdAndNumber(tenantId, journalNumber);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private Mono<JournalEntry> toDomain(JournalEntryEntity header) {
        return lineRepository.findByJournalEntryIdOrderByLineNumberAsc(header.id())
                .map(this::toLineDomain)
                .collectList()
                .map(lines -> JournalEntry.rehydrate(
                        header.id(), header.tenantId(), header.organizationId(),
                        JournalNumber.of(header.number()),
                        JournalType.valueOf(header.type()),
                        header.referenceType(), header.referenceId(), lines,
                        header.description(), JournalStatus.valueOf(header.status()),
                        header.createdByUserId(), header.postedAt(),
                        header.kernelInvoiceId(),       // nullable Kernel invoice ref
                        header.kernelJournalEntryId(),  // nullable Kernel JE ref
                        header.createdAt(), header.updatedAt()));
    }

    private JournalEntryEntity toHeaderEntity(JournalEntry entry) {
        return new JournalEntryEntity(
                entry.getId(), entry.getTenantId(), entry.getOrganizationId(),
                entry.getNumber().value(), entry.getType().name(),
                entry.getReferenceType(), entry.getReferenceId(),
                entry.getDescription(), entry.getStatus().name(),
                entry.getCreatedByUserId(), entry.getPostedAt(),
                entry.getKernelInvoiceId(),       // nullable
                entry.getKernelJournalEntryId(),  // nullable
                entry.getCreatedAt(), entry.getUpdatedAt());
    }

    private JournalEntryLineEntity toLineEntity(UUID journalEntryId, JournalEntryLine line) {
        return new JournalEntryLineEntity(
                UUID.randomUUID(), journalEntryId, line.lineNumber(),
                line.accountId(), line.accountCode(), line.label(),
                line.debitAmount(), line.creditAmount(), line.currency());
    }

    private JournalEntryLine toLineDomain(JournalEntryLineEntity entity) {
        return new JournalEntryLine(entity.lineNumber(), entity.accountId(), entity.accountCode(),
                entity.label(), entity.debitAmount(), entity.creditAmount(), entity.currency());
    }
}
