package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.ReconciliationRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

/**
 * IReconciliationUseCase — primary port for periodic wallet reconciliation.
 * Verifies that wallet transactions match provider statements.
 *
 * @author MANFOUO Braun
 */
public interface IReconciliationUseCase {

    /**
     * Runs reconciliation for a tenant for the given month.
     * Compares wallet transaction totals against provider statement totals.
     *
     * @param tenantId tenant identifier
     * @param period   year-month to reconcile
     * @return reconciliation result record
     */
    Mono<ReconciliationRecord> runReconciliation(UUID tenantId, YearMonth period);

    /**
     * Retrieves all reconciliation records for a tenant.
     *
     * @param tenantId tenant identifier
     * @return stream of reconciliation records
     */
    Flux<ReconciliationRecord> getReconciliationHistory(UUID tenantId);

    /**
     * Resolves a discrepancy with an explanatory note.
     *
     * @param reconciliationId record identifier
     * @param note             resolution note
     * @return updated record
     */
    Mono<ReconciliationRecord> resolveDiscrepancy(UUID reconciliationId, String note);
}
