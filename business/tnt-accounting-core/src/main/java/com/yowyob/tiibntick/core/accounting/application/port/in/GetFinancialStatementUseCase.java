package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.FinancialStatement;
import com.yowyob.tiibntick.core.accounting.domain.model.StatementType;
import reactor.core.publisher.Mono;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Use case — Generate a financial statement (P&amp;L, Balance Sheet, Trial Balance).
 * @author MANFOUO Braun
 */
public interface GetFinancialStatementUseCase {
    Mono<FinancialStatement> getFinancialStatement(UUID tenantId, YearMonth period, StatementType type);
}
