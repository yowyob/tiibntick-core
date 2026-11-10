package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelChallengeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelReceiveCodeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletRechargeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IKernelWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletHoldUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelPaymentGatewayPort;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletFrozenException;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thin orchestration over the Kernel wallet: resolves the caller's own wallet id from the
 * Kernel on every call (never from client input) and delegates. No local ledger is touched.
 *
 * <p>The Kernel has no freeze/reservation, so every <em>outgoing</em> operation (transfer,
 * withdrawal) first checks {@code Kernel balance − active local holds} (see
 * {@link IWalletHoldUseCase}) and is refused with {@link WalletFrozenException} when it would
 * dip into funds held for an open incident or dispute.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KernelWalletService implements IKernelWalletUseCase {

    private final IKernelPaymentGatewayPort kernel;
    private final IWalletHoldUseCase holds;

    @Override
    public Mono<KernelWalletDto> getMyWallet() {
        return kernel.getMyWallet();
    }

    @Override
    public Mono<KernelWalletDto> getOrCreateMyWallet(String label) {
        return kernel.getMyWallet()
                .switchIfEmpty(Mono.defer(() -> kernel.createMyWallet(label)));
    }

    @Override
    public Flux<JsonNode> getMyTransactions() {
        return myWalletId().flatMapMany(kernel::getTransactionsRaw);
    }

    @Override
    public Mono<KernelWalletRechargeDto> recharge(BigDecimal amount, String currency, String provider,
                                                  String method, String payerReference, String idempotencyKey) {
        return myWalletId().flatMap(id ->
                kernel.recharge(id, amount, currency, provider, method, payerReference, idempotencyKey));
    }

    @Override
    public Mono<KernelWalletRechargeDto> getRechargeOrder(UUID orderId) {
        return kernel.getRechargeOrder(orderId);
    }

    @Override
    public Mono<KernelWalletRechargeDto> refreshRechargeOrder(UUID orderId) {
        return kernel.refreshRechargeOrder(orderId);
    }

    @Override
    public Flux<KernelWalletRechargeDto> listMyRechargeOrders(Integer limit) {
        return myWalletId().flatMapMany(id -> kernel.listRechargeOrders(id, limit));
    }

    @Override
    public Mono<KernelReceiveCodeDto> generateReceiveCode() {
        return myWalletId().flatMap(kernel::generateReceiveCode);
    }

    @Override
    public Mono<KernelChallengeDto> transferChallenge(String recipientCode, BigDecimal amount, String reference) {
        return myWalletForSpending(amount)
                .flatMap(w -> kernel.transferChallenge(w.id(), recipientCode, amount, reference));
    }

    @Override
    public Mono<JsonNode> transfer(String recipientCode, BigDecimal amount, String reference,
                                   String challengeToken, String code) {
        return myWalletForSpending(amount)
                .flatMap(w -> kernel.transfer(w.id(), recipientCode, amount, reference, challengeToken, code));
    }

    @Override
    public Mono<KernelChallengeDto> withdrawalChallenge(BigDecimal amount, String phoneNumber) {
        return myWalletForSpending(amount)
                .flatMap(w -> kernel.withdrawalChallenge(w.id(), amount, phoneNumber));
    }

    @Override
    public Mono<JsonNode> withdraw(BigDecimal amount, String currency, String phoneNumber, String method,
                                   String recipientName, String challengeToken, String code) {
        return myWalletForSpending(amount)
                .flatMap(w -> kernel.withdraw(w.id(), amount, currency, phoneNumber, method,
                        recipientName, challengeToken, code));
    }

    @Override
    public Mono<Boolean> withdrawalsAvailable() {
        return kernel.withdrawalsAvailable();
    }

    private Mono<UUID> myWalletId() {
        return kernel.getMyWallet()
                .map(KernelWalletDto::id)
                .switchIfEmpty(Mono.error(new WalletNotFoundException(
                        "No Kernel wallet for the authenticated user — create it first (POST /billing/kernel-wallet/me)")));
    }

    /**
     * Resolves the caller's wallet and verifies that {@code amount} fits in
     * {@code balance − active holds}. Fails closed on an unknown balance only when holds exist.
     */
    private Mono<KernelWalletDto> myWalletForSpending(BigDecimal amount) {
        return kernel.getMyWallet()
                .switchIfEmpty(Mono.error(new WalletNotFoundException(
                        "No Kernel wallet for the authenticated user — create it first (POST /billing/kernel-wallet/me)")))
                .flatMap(w -> {
                    if (w.ownerId() == null) {
                        return Mono.just(w);
                    }
                    return holds.activeHeldAmount(w.ownerId(), null)
                            .flatMap(held -> {
                                if (held.signum() == 0) {
                                    return Mono.just(w);
                                }
                                BigDecimal balance = w.balance() != null ? w.balance() : BigDecimal.ZERO;
                                BigDecimal spendable = balance.subtract(held);
                                if (amount != null && amount.compareTo(spendable) > 0) {
                                    log.warn("[Hold] refused outgoing {} for user={}: balance={} held={}",
                                            amount, w.ownerId(), balance, held);
                                    return Mono.<KernelWalletDto>error(new WalletFrozenException(
                                            "Funds are held for an open incident or dispute: spendable balance is "
                                                    + spendable.max(BigDecimal.ZERO).toPlainString()
                                                    + " (held " + held.toPlainString() + ")"));
                                }
                                return Mono.just(w);
                            });
                });
    }
}
