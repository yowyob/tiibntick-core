package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelChallengeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelReceiveCodeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletRechargeDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Primary port for the caller's <strong>own</strong> Kernel-managed wallet (target model of the
 * Kernel wallet migration). Every operation acts on the wallet the Kernel resolves for the
 * authenticated caller ({@code GET /api/payments/wallets/mine}) — no operation accepts a wallet
 * or owner id, so there is no cross-user surface. The caller's bearer token is forwarded to the
 * Kernel, which also enforces ownership and the one-time-code confirmation of money movements.
 *
 * @author MANFOUO Braun
 */
public interface IKernelWalletUseCase {

    /** The caller's wallet; empty when none exists yet. */
    Mono<KernelWalletDto> getMyWallet();

    /** Returns the caller's wallet, creating it on the Kernel the first time. */
    Mono<KernelWalletDto> getOrCreateMyWallet(String label);

    /** Raw Kernel transactions of the caller's wallet (the Kernel's item schema is ambiguous). */
    Flux<JsonNode> getMyTransactions();

    Mono<KernelWalletRechargeDto> recharge(BigDecimal amount, String currency, String provider,
                                           String method, String payerReference, String idempotencyKey);

    Mono<KernelWalletRechargeDto> getRechargeOrder(UUID orderId);

    Mono<KernelWalletRechargeDto> refreshRechargeOrder(UUID orderId);

    Flux<KernelWalletRechargeDto> listMyRechargeOrders(Integer limit);

    /** Temporary code another user (or the Core, on their behalf) pays to. */
    Mono<KernelReceiveCodeDto> generateReceiveCode();

    Mono<KernelChallengeDto> transferChallenge(String recipientCode, BigDecimal amount, String reference);

    Mono<JsonNode> transfer(String recipientCode, BigDecimal amount, String reference,
                            String challengeToken, String code);

    Mono<KernelChallengeDto> withdrawalChallenge(BigDecimal amount, String phoneNumber);

    Mono<JsonNode> withdraw(BigDecimal amount, String currency, String phoneNumber, String method,
                            String recipientName, String challengeToken, String code);

    Mono<Boolean> withdrawalsAvailable();
}
