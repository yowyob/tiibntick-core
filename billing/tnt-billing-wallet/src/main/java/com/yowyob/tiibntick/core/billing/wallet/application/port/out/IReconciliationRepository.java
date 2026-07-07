package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.ReconciliationRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Secondary port — persistence contract for ReconciliationRecord entities.
 * @author MANFOUO Braun
 */
public interface IReconciliationRepository {
    Mono<ReconciliationRecord> save(ReconciliationRecord record);
    Mono<ReconciliationRecord> findById(UUID id);
    Flux<ReconciliationRecord> findByTenantId(UUID tenantId);
    Mono<ReconciliationRecord> findByTenantIdAndPeriod(UUID tenantId, YearMonth period);
}
