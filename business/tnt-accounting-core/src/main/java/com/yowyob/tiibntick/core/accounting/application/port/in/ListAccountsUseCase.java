package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import reactor.core.publisher.Flux;
import java.util.UUID;

/**
 * Use case — List accounts in the tenant's chart of accounts.
 * @author MANFOUO Braun
 */
public interface ListAccountsUseCase {
    Flux<Account> listAccounts(UUID tenantId, boolean activeOnly);
}
