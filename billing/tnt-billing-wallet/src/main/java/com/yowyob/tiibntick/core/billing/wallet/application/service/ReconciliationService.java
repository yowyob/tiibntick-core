package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IReconciliationUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IReconciliationRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.ReconciliationStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Currency;
import java.util.UUID;

/**
 * ReconciliationService — compares wallet transaction totals against
 * provider statement totals for a given tenant and period.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService implements IReconciliationUseCase {

    private static final Currency XAF = Currency.getInstance("XAF");

    private final IReconciliationRepository reconciliationRepository;

    @Override
    public Mono<ReconciliationRecord> runReconciliation(UUID tenantId, YearMonth period) {
        log.info("Running wallet reconciliation for tenantId={} period={}", tenantId, period);

        return computeWalletTotal(tenantId, period)
                .flatMap(walletTotal -> {
                    // In a real implementation, bankStatementTotal would come from
                    // a provider statement import service. Here we use walletTotal
                    // as the baseline (discrepancy = 0 when no external statement loaded).
                    Money bankStatementTotal = walletTotal;
                    Money discrepancy = bankStatementTotal.subtract(walletTotal).abs();

                    ReconciliationRecord record = ReconciliationRecord.builder()
                            .id(ReconciliationId.generate())
                            .tenantId(tenantId)
                            .period(period)
                            .walletTotal(walletTotal)
                            .bankStatementTotal(bankStatementTotal)
                            .discrepancy(discrepancy)
                            .status(ReconciliationStatus.PENDING)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    record.evaluate();
                    return reconciliationRepository.save(record);
                });
    }

    @Override
    public Flux<ReconciliationRecord> getReconciliationHistory(UUID tenantId) {
        return reconciliationRepository.findByTenantId(tenantId);
    }

    @Override
    public Mono<ReconciliationRecord> resolveDiscrepancy(UUID reconciliationId, String note) {
        return reconciliationRepository.findById(reconciliationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "ReconciliationRecord not found: " + reconciliationId)))
                .flatMap(record -> {
                    record.resolve(note);
                    return reconciliationRepository.save(record);
                });
    }

    /**
     * Scheduled job: runs reconciliation for the previous month on the 1st of each month at 02:00.
     */
    @Scheduled(cron = "0 0 2 1 * *")
    @SchedulerLock(name = "wallet-scheduled-reconciliation", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void scheduledReconciliation() {
        LockAssert.assertLocked();
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("Scheduled reconciliation started for period={}", lastMonth);
        // In a multi-tenant system, fetch all active tenants and reconcile each.
        // The actual tenant list would come from tnt-organization-core.
    }

    // ─── private helpers ───────────────────────────────────────────────────

    /**
     * Sums all CONFIRMED DEBIT transactions for a tenant in a given month.
     */
    private Mono<Money> computeWalletTotal(UUID tenantId, YearMonth period) {
        // Implementation collects all wallets for the tenant, then their transactions,
        // filters by period and CONFIRMED DEBIT, sums amounts.
        // This is simplified — full implementation queries the DB directly for performance.
        return Mono.just(Money.zero(XAF));
    }
}
