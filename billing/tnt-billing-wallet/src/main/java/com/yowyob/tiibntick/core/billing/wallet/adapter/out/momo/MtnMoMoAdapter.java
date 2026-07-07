package com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo.dto.MtnPaymentStatusResponse;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo.dto.MtnTokenResponse;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IMoMoPaymentPort;
import com.yowyob.tiibntick.core.billing.wallet.config.MtnMoMoProperties;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.MoMoPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MtnMoMoAdapter — integrates with MTN MoMo Collections API v2.
 * Handles OAuth2 token acquisition, collection request initiation, and status checks.
 * See: https://momodeveloper.mtn.com/docs/services/collection/
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtnMoMoAdapter implements IMoMoPaymentPort {

    private final WebClient mtnWebClient;
    private final MtnMoMoProperties properties;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();

    @Override
    public Mono<String> initiateMtnCollection(MoMoPayload payload, String referenceId) {
        return getAccessToken()
                .flatMap(token -> mtnWebClient.post()
                        .uri("/collection/v1_0/requesttopay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Reference-Id", referenceId)
                        .header("X-Target-Environment", properties.environment())
                        .header("Ocp-Apim-Subscription-Key", properties.subscriptionKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload.toMtnRequestBody())
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(
                                                new RuntimeException("MTN MoMo error: " + body))))
                        .toBodilessEntity()
                        .doOnSuccess(r -> log.info("MTN MoMo collection initiated, referenceId={}", referenceId))
                        .thenReturn(referenceId));
    }

    @Override
    public Mono<String> initiateOrangePayment(MoMoPayload payload) {
        // Delegated to OrangeMoneyAdapter — this adapter handles MTN only
        return Mono.error(new UnsupportedOperationException(
                "Orange Money is handled by OrangeMoneyAdapter"));
    }

    @Override
    public Mono<String> checkMtnPaymentStatus(String referenceId) {
        return getAccessToken()
                .flatMap(token -> mtnWebClient.get()
                        .uri("/collection/v1_0/requesttopay/" + referenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Target-Environment", properties.environment())
                        .header("Ocp-Apim-Subscription-Key", properties.subscriptionKey())
                        .retrieve()
                        .bodyToMono(MtnPaymentStatusResponse.class)
                        .map(MtnPaymentStatusResponse::status));
    }

    @Override
    public Mono<String> initiateStripePayment(MoMoPayload payload, String idempotencyKey) {
        return Mono.error(new UnsupportedOperationException(
                "Stripe is handled by StripeAdapter"));
    }

    @Override
    public Mono<String> refundStripePayment(String stripePaymentIntentId) {
        return Mono.error(new UnsupportedOperationException(
                "Stripe is handled by StripeAdapter"));
    }

    /**
     * Acquires an OAuth2 bearer token from the MTN API.
     * Uses Basic Auth: Base64(userId:apiKey).
     */
    private Mono<String> getAccessToken() {
        String cached = cachedToken.get();
        if (cached != null && !cached.isBlank()) {
            return Mono.just(cached);
        }
        String credentials = Base64.getEncoder().encodeToString(
                (properties.userId() + ":" + properties.apiKey()).getBytes());

        return mtnWebClient.post()
                .uri("/collection/token/")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .header("Ocp-Apim-Subscription-Key", properties.subscriptionKey())
                .retrieve()
                .bodyToMono(MtnTokenResponse.class)
                .map(response -> {
                    cachedToken.set(response.accessToken());
                    return response.accessToken();
                });
    }
}
