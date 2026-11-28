package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Debts towards the compensation fund (lot L5, QO-22 / D-115, QO-26 / D-120). Every write is idempotent per
 * {@code reference}.
 */
public interface IDebtUseCase {

    record NewDebt(UUID tenantId, WalletDebt.DebtorType debtorType, UUID debtorId, WalletDebt.Reason reason,
                   String reference, UUID deliveryId, BigDecimal amount, String currency) {
    }

    /** The fund paid: the debt is OPEN and its recovery (a Kernel cash collection on the debtor's wallet) opened. */
    Mono<WalletDebt> record(NewDebt debt);

    /** The fund will pay later (a return at the courier's expense): PENDING until {@link #activate}. */
    Mono<WalletDebt> recordPending(NewDebt debt);

    /** The fund paid {@code amount} for a PENDING debt: OPEN, recovery opened. No debt: empty. */
    Mono<WalletDebt> activate(String reference, BigDecimal amount);

    /** The paid event never happened (the return was cancelled): a PENDING debt is CANCELLED. */
    Mono<WalletDebt> cancelPending(String reference, String note);

    /** Debts of the caller (a courier, or the organisation he acts for), newest first. */
    Flux<WalletDebt> mine(UUID callerActorId, UUID callerOrganizationId);

    /** The debtor asks the Kernel for the code that confirms the remittance. */
    Mono<Challenge> repayChallenge(UUID debtId, UUID callerActorId, UUID callerOrganizationId);

    /** The debtor remits the debt to the fund with the code: REPAID. */
    Mono<WalletDebt> repay(UUID debtId, UUID callerActorId, UUID callerOrganizationId, String token, String code);

    // ── administrator (admin spec §5.2) ─────────────────────────────────────

    Flux<WalletDebt> list(WalletDebt.Status status, int limit);

    /** Names the debtor of a debt recorded without one (relay, agency) and opens its recovery. */
    Mono<WalletDebt> assign(UUID debtId, UUID debtorId);

    /** Retries opening the recovery of an OPEN debt (debtor without wallet at the time, Kernel down…). */
    Mono<WalletDebt> retryRecovery(UUID debtId);

    /** Writes a debt off: CANCELLED, its Kernel cash collection cancelled. */
    Mono<WalletDebt> writeOff(UUID debtId, String note);
}
