package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** {@code billing.wallet_debts} (lot L5, QO-22 / QO-26). */
public interface IDebtRepository {

    Mono<WalletDebt> findById(UUID id);

    Mono<WalletDebt> findByReference(String reference);

    Mono<WalletDebt> save(WalletDebt debt, boolean isNew);

    /** Debts of one debtor, newest first. */
    Flux<WalletDebt> byDebtor(UUID debtorId);

    /** Debts in one status ({@code null}: all), newest first, at most {@code limit}. */
    Flux<WalletDebt> byStatus(WalletDebt.Status status, int limit);
}
