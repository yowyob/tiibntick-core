package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.mapper.WalletPersistenceMapper;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.ReconciliationR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IReconciliationRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.ReconciliationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Adapter — implements {@link IReconciliationRepository} using Spring Data R2DBC against
 * the {@code billing.wallet_reconciliations} table.
 *
 * <p>Replaces {@code InMemoryReconciliationRepository} (Chantier D · Audit n°6 · S3): a
 * {@code ConcurrentHashMap} is only consistent within a single JVM — every application
 * instance in a multi-instance deployment held its own independent, empty-on-restart copy
 * of every reconciliation record, so two instances could disagree about a tenant's
 * reconciliation history or silently lose records on redeploy. The table itself already
 * existed in the Liquibase changelog; only the adapter was missing.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class ReconciliationRepositoryAdapter implements IReconciliationRepository {

    private final ReconciliationR2dbcRepository repo;
    private final WalletPersistenceMapper mapper;

    @Override
    public Mono<ReconciliationRecord> save(ReconciliationRecord record) {
        return repo.existsById(record.getId().value())
                .flatMap(exists -> {
                    var entity = mapper.toEntity(record);
                    entity.setNew(!exists);
                    return repo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ReconciliationRecord> findById(UUID id) {
        return repo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Flux<ReconciliationRecord> findByTenantId(UUID tenantId) {
        return repo.findByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<ReconciliationRecord> findByTenantIdAndPeriod(UUID tenantId, YearMonth period) {
        return repo.findByTenantIdAndPeriodYearAndPeriodMonth(tenantId, period.getYear(), period.getMonthValue())
                .map(mapper::toDomain);
    }
}
