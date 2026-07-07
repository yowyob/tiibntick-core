package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.AccountingPeriod;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Close an accounting period to prevent further entry posting.
 * @author MANFOUO Braun
 */
public interface CloseAccountingPeriodUseCase {
    Mono<AccountingPeriod> closePeriod(UUID tenantId, int year, int month, String closedByUserId);
}
