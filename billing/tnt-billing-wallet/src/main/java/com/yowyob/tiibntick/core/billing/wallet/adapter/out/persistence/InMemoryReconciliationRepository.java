package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IReconciliationRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.ReconciliationRecord;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory placeholder for {@link IReconciliationRepository}.
 *
 * <p>{@code ReconciliationService} is still a stub itself (see its
 * {@code computeWalletTotal} comment) — no R2DBC entity/table exists yet for
 * reconciliation records. Records are lost on restart; replace with a real
 * R2DBC-backed adapter once the persistence schema is defined.
 *
 * @author MANFOUO Braun
 */
@Repository
public class InMemoryReconciliationRepository implements IReconciliationRepository {

    private final Map<UUID, ReconciliationRecord> store = new ConcurrentHashMap<>();

    @Override
    public Mono<ReconciliationRecord> save(ReconciliationRecord record) {
        store.put(record.getId().value(), record);
        return Mono.just(record);
    }

    @Override
    public Mono<ReconciliationRecord> findById(UUID id) {
        return Mono.justOrEmpty(store.get(id));
    }

    @Override
    public Flux<ReconciliationRecord> findByTenantId(UUID tenantId) {
        return Flux.fromIterable(store.values())
                .filter(record -> record.getTenantId().equals(tenantId));
    }

    @Override
    public Mono<ReconciliationRecord> findByTenantIdAndPeriod(UUID tenantId, YearMonth period) {
        return Flux.fromIterable(store.values())
                .filter(record -> record.getTenantId().equals(tenantId) && record.getPeriod().equals(period))
                .next();
    }
}
