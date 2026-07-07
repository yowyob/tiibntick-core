package com.yowyob.tiibntick.core.accounting.application.port.out;

import com.yowyob.tiibntick.core.accounting.domain.model.AccountingPeriod;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound port for AccountingPeriod persistence. Author: MANFOUO Braun */
public interface AccountingPeriodRepository {
    Mono<AccountingPeriod> save(AccountingPeriod period);
    Mono<AccountingPeriod> findByTenantIdAndYearAndMonth(UUID tenantId, int year, int month);
    Mono<AccountingPeriod> findOrCreateOpen(UUID tenantId, int year, int month);
}
