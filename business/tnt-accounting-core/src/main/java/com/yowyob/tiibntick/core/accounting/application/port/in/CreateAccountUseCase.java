package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.*;
import reactor.core.publisher.Mono;


/**
 * Use case: Create a new account in the chart of accounts.
 * Author: MANFOUO Braun
 */
public interface CreateAccountUseCase {
    Mono<Account> createAccount(CreateAccountCommand command);
}
