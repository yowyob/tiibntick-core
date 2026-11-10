package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelChallengeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelReceiveCodeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletRechargeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IKernelWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelPaymentGatewayPort;
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
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KernelWalletService implements IKernelWalletUseCase {

    private final IKernelPaymentGatewayPort kernel;

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
        return myWalletId().flatMap(id -> kernel.transferChallenge(id, recipientCode, amount, reference));
    }

    @Override
    public Mono<JsonNode> transfer(String recipientCode, BigDecimal amount, String reference,
                                   String challengeToken, String code) {
        return myWalletId().flatMap(id ->
                kernel.transfer(id, recipientCode, amount, reference, challengeToken, code));
    }

    @Override
    public Mono<KernelChallengeDto> withdrawalChallenge(BigDecimal amount, String phoneNumber) {
        return myWalletId().flatMap(id -> kernel.withdrawalChallenge(id, amount, phoneNumber));
    }

    @Override
    public Mono<JsonNode> withdraw(BigDecimal amount, String currency, String phoneNumber, String method,
                                   String recipientName, String challengeToken, String code) {
        return myWalletId().flatMap(id ->
                kernel.withdraw(id, amount, currency, phoneNumber, method, recipientName, challengeToken, code));
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
}
