package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelPaymentOrderDto;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelWalletDto;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.dto.KernelWalletTransactionDto;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelPaymentGatewayPort;
import com.yowyob.tiibntick.common.kernel.KernelResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
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
}
