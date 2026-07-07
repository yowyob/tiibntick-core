package com.yowyob.tiibntick.core.accounting.application.port.in;

import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Seed the OHADA standard chart of accounts for a new tenant.
 * Idempotent: no-op if already initialized.
 * @author MANFOUO Braun
 */
public interface InitializeOhadaChartUseCase {
    Mono<Void> initializeForTenant(UUID tenantId, String defaultCurrency);
}
