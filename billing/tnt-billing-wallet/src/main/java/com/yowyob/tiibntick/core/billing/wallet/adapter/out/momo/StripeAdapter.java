package com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo;

import com.yowyob.tiibntick.core.billing.wallet.config.StripeProperties;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.MoMoPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * StripeAdapter — integrates with Stripe API for card-based payments.
 * Creates PaymentIntents and handles refunds.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeAdapter {

    private final WebClient stripeWebClient;
    private final StripeProperties properties;

    /**
     * Creates a Stripe PaymentIntent and returns the client_secret.
     */
    public Mono<String> createPaymentIntent(MoMoPayload payload, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // Convert to minor currency units (XAF has 0 decimal places but Stripe requires minor)
        form.add("amount", String.valueOf(
                Long.parseLong(payload.amount()) * 100));
        form.add("currency", payload.currency().toLowerCase());
        form.add("description", payload.payerMessage());
        form.add("metadata[external_id]", payload.externalId());

        return stripeWebClient.post()
                .uri("/v1/payment_intents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("client_secret"))
                .doOnSuccess(s -> log.info("Stripe PaymentIntent created"));
    }

    /**
     * Creates a Stripe Refund for a given PaymentIntent.
     */
    public Mono<String> refund(String paymentIntentId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntentId);

        return stripeWebClient.post()
                .uri("/v1/refunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("id"))
                .doOnSuccess(id -> log.info("Stripe refund created: {}", id));
    }
}
