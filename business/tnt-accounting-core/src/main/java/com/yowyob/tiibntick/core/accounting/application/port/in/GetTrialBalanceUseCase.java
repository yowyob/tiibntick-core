package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.FinancialStatement;
import reactor.core.publisher.Mono;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Use case — Generate the trial balance for a tenant and period.
 * @author MANFOUO Braun
 */
public interface GetTrialBalanceUseCase {
    Mono<FinancialStatement> getTrialBalance(UUID tenantId, YearMonth period);
}
