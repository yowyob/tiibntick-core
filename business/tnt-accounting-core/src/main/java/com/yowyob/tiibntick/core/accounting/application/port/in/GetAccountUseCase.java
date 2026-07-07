package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use case: Retrieve a single account by ID.
 * Author: MANFOUO Braun
 */
public interface GetAccountUseCase {
    Mono<Account> getAccount(UUID tenantId, UUID accountId);
    Mono<Account> getAccountByCode(UUID tenantId, String code);
}
