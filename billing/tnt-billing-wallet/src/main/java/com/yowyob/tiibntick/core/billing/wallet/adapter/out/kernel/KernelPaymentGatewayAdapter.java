package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelChallengeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelPaymentOrderDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelReceiveCodeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletRechargeDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletTransactionDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelPaymentGatewayPort;
import com.yowyob.tiibntick.common.kernel.KernelResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the Kernel's {@code payment-controller} ({@code /api/payments/wallets/**}) via the
 * shared {@code kernelPaymentWebClient} bean (defined once in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig} — carries the Core's own {@code X-Api-Key}/{@code X-Client-Id}/
 * {@code X-Solution-Code} identity, same as every other Kernel adapter in this repo).
 *
 * <p>Never injects a Kernel Spring bean/type — only the generic {@link WebClient} (see root
 * {@code CLAUDE.md}: Kernel is HTTP-only). Responses are unwrapped via {@link KernelResponses}
 * so the {@code {success, data, ...}} envelope is never skipped (ADR-012).
 *
 * <p>See {@link IKernelPaymentGatewayPort} javadoc for exactly which operations this covers
 * and which it deliberately does not (provider dispatch, confirmation callback/polling,
 * freeze, refund — none exist in the Kernel's published API as of this workstream).
 *
 * @author MANFOUO Braun
 */
@Slf4j
public class KernelPaymentGatewayAdapter implements IKernelPaymentGatewayPort {

    private final WebClient kernelPaymentWebClient;

    public KernelPaymentGatewayAdapter(WebClient kernelPaymentWebClient) {
        this.kernelPaymentWebClient = kernelPaymentWebClient;
    }

    @Override
    public Mono<KernelWalletDto> createWallet(UUID organizationId, String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (organizationId != null) {
            body.put("organizationId", organizationId.toString());
        }
        if (label != null) {
            body.put("label", label);
        }
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets")
                        .bodyValue(body)
                        .retrieve(),
                KernelWalletDto.class);
    }

    @Override
    public Mono<KernelWalletDto> getWallet(UUID walletId) {
        return KernelResponses.unwrapObject(
                kernelPaymentWebClient.get()
                        .uri("/api/payments/wallets/{walletId}", walletId)
                        .retrieve(),
                KernelWalletDto.class, log, "getWallet(" + walletId + ")");
    }

    @Override
    public Mono<KernelWalletDto> getWalletByOwner(UUID ownerId) {
        return KernelResponses.unwrapObject(
                kernelPaymentWebClient.get()
                        .uri("/api/payments/wallets/owner/{ownerId}", ownerId)
                        .retrieve(),
                KernelWalletDto.class, log, "getWalletByOwner(" + ownerId + ")");
    }

    @Override
    public Mono<Boolean> canOperate(UUID walletId, BigDecimal amount) {
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/payments/wallets/{walletId}/can-operate")
                                .queryParam("amount", amount)
                                .build(walletId))
                        .retrieve(),
                Boolean.class);
    }

    @Override
    public Mono<KernelWalletTransactionDto> pay(UUID walletId, BigDecimal amount, String currency,
                                                 String reference, String description) {
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/pay", walletId)
                        .bodyValue(transactionRequestBody(amount, currency, reference, description))
                        .retrieve(),
                KernelWalletTransactionDto.class);
    }

    @Override
    public Mono<KernelWalletTransactionDto> recharge(UUID walletId, BigDecimal amount, String currency,
                                                      String reference, String description) {
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/recharge", walletId)
                        .bodyValue(transactionRequestBody(amount, currency, reference, description))
                        .retrieve(),
                KernelWalletTransactionDto.class);
    }

    @Override
    public Flux<KernelWalletTransactionDto> getTransactions(UUID walletId) {
        return KernelResponses.unwrapList(
                kernelPaymentWebClient.get()
                        .uri("/api/payments/wallets/{walletId}/transactions", walletId)
                        .retrieve(),
                KernelWalletTransactionDto.class, log, "getTransactions(" + walletId + ")");
    }

    @Override
    public Mono<KernelPaymentOrderDto> initiateOrder(String provider, String method, BigDecimal amount,
                                                       String currency, String payerReference,
                                                       String description, String callbackUrl,
                                                       String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", idempotencyKey);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("provider", provider);
        body.put("method", method);
        if (payerReference != null) {
            body.put("payerReference", payerReference);
        }
        if (description != null) {
            body.put("description", description);
        }
        if (callbackUrl != null) {
            body.put("callbackUrl", callbackUrl);
        }
        // "clientId"/"serviceCode" are deliberately omitted: both are optional per the
        // Kernel's InitiatePaymentRequest schema and their exact semantics (vs. the
        // X-Client-Id header already sent on every request via kernelPaymentWebClient) are
        // not confirmed — see IKernelPaymentGatewayPort javadoc.
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/orders")
                        .bodyValue(body)
                        .retrieve(),
                KernelPaymentOrderDto.class);
    }

    @Override
    public Mono<KernelPaymentOrderDto> refreshOrder(String kernelOrderId) {
        return KernelResponses.unwrapObject(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/orders/{id}/refresh", kernelOrderId)
                        .retrieve(),
                KernelPaymentOrderDto.class, log, "refreshOrder(" + kernelOrderId + ")");
    }

    /**
     * Best-effort request body for {@code pay}/{@code recharge} — see
     * {@link KernelWalletTransactionDto} javadoc for why the exact field names cannot be
     * confirmed from the Kernel's published schema (a {@code TransactionRequest} name
     * collision with the blockchain-controller's unrelated DTO).
     */
    private Map<String, Object> transactionRequestBody(BigDecimal amount, String currency,
                                                         String reference, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("reference", reference);
        body.put("description", description);
        return body;
    }

    // ── User-bound wallet operations (Kernel wallet migration) ─────────────────

    @Override
    public Mono<KernelWalletDto> getMyWallet() {
        // 404 = "no wallet yet" -> empty; every other error propagates (never mask an auth failure).
        return KernelResponses.unwrapObjectOrPropagate(
                        kernelPaymentWebClient.get().uri("/api/payments/wallets/mine").retrieve(),
                        KernelWalletDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    @Override
    public Mono<KernelWalletDto> createMyWallet(String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (label != null) {
            body.put("label", label);
        }
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post().uri("/api/payments/wallets/mine").bodyValue(body).retrieve(),
                KernelWalletDto.class);
    }

    @Override
    public Mono<KernelWalletRechargeDto> recharge(UUID walletId, BigDecimal amount, String currency,
                                                  String provider, String method, String payerReference,
                                                  String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("provider", provider);
        body.put("method", method);
        if (payerReference != null) {
            body.put("payerReference", payerReference);
        }
        body.put("idempotencyKey", idempotencyKey);
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/recharge", walletId)
                        .bodyValue(body).retrieve(),
                KernelWalletRechargeDto.class);
    }

    @Override
    public Mono<KernelWalletRechargeDto> getRechargeOrder(UUID orderId) {
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.get()
                        .uri("/api/payments/wallets/recharge-orders/{orderId}", orderId).retrieve(),
                KernelWalletRechargeDto.class);
    }

    @Override
    public Mono<KernelWalletRechargeDto> refreshRechargeOrder(UUID orderId) {
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/recharge-orders/{orderId}/refresh", orderId).retrieve(),
                KernelWalletRechargeDto.class);
    }

    @Override
    public Flux<KernelWalletRechargeDto> listRechargeOrders(UUID walletId, Integer limit) {
        return KernelResponses.unwrapListOrPropagate(
                kernelPaymentWebClient.get()
                        .uri(b -> {
                            b.path("/api/payments/wallets/{walletId}/recharge-orders");
                            if (limit != null) {
                                b.queryParam("limit", limit);
                            }
                            return b.build(walletId);
                        })
                        .retrieve(),
                KernelWalletRechargeDto.class);
    }

    @Override
    public Mono<KernelReceiveCodeDto> generateReceiveCode(UUID walletId) {
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/receive-codes", walletId).retrieve(),
                KernelReceiveCodeDto.class);
    }

    @Override
    public Mono<KernelChallengeDto> transferChallenge(UUID walletId, String recipientCode,
                                                      BigDecimal amount, String reference) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientCode", recipientCode);
        body.put("amount", amount);
        if (reference != null) {
            body.put("reference", reference);
        }
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/transfers/challenge", walletId)
                        .bodyValue(body).retrieve(),
                KernelChallengeDto.class);
    }

    @Override
    public Mono<JsonNode> transfer(UUID walletId, String recipientCode, BigDecimal amount, String reference,
                                   String challengeToken, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientCode", recipientCode);
        body.put("amount", amount);
        if (reference != null) {
            body.put("reference", reference);
        }
        body.put("challengeToken", challengeToken);
        body.put("code", code);
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/transfers", walletId)
                        .bodyValue(body).retrieve(),
                JsonNode.class);
    }

    @Override
    public Mono<KernelChallengeDto> withdrawalChallenge(UUID walletId, BigDecimal amount, String phoneNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("phoneNumber", phoneNumber);
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/withdrawals/challenge", walletId)
                        .bodyValue(body).retrieve(),
                KernelChallengeDto.class);
    }

    @Override
    public Mono<JsonNode> withdraw(UUID walletId, BigDecimal amount, String currency, String phoneNumber,
                                   String method, String recipientName, String challengeToken, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("phoneNumber", phoneNumber);
        body.put("method", method);
        if (recipientName != null) {
            body.put("recipientName", recipientName);
        }
        body.put("challengeToken", challengeToken);
        body.put("code", code);
        return KernelResponses.unwrapObjectOrPropagate(
                kernelPaymentWebClient.post()
                        .uri("/api/payments/wallets/{walletId}/withdrawals", walletId)
                        .bodyValue(body).retrieve(),
                JsonNode.class);
    }

    @Override
    public Mono<Boolean> withdrawalsAvailable() {
        record Availability(Boolean available) { }
        return KernelResponses.unwrapObjectOrPropagate(
                        kernelPaymentWebClient.get().uri("/api/payments/withdrawals/availability").retrieve(),
                        Availability.class)
                .map(a -> Boolean.TRUE.equals(a.available()));
    }

    @Override
    public Flux<JsonNode> getTransactionsRaw(UUID walletId) {
        return KernelResponses.unwrapListOrPropagate(
                kernelPaymentWebClient.get()
                        .uri("/api/payments/wallets/{walletId}/transactions", walletId).retrieve(),
                JsonNode.class);
    }
}
