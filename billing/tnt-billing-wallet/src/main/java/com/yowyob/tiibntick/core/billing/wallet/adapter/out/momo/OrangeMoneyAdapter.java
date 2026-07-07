package com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo;

import com.yowyob.tiibntick.core.billing.wallet.config.OrangeMoneyProperties;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.MoMoPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * OrangeMoneyAdapter — integrates with Orange Money Cameroon Payment API.
 * Uses OTP-based redirect flow.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrangeMoneyAdapter {

    private final WebClient orangeWebClient;
    private final OrangeMoneyProperties properties;

    /**
     * Initiates an Orange Money payment request.
     * Returns the redirect URL for the payment page.
     */
    public Mono<String> initiatePayment(MoMoPayload payload) {
        Map<String, Object> body = payload.toOrangeRequestBody();
        body.put("merchant_key", properties.merchantKey());
        body.put("return_url", properties.returnUrl());
        body.put("cancel_url", properties.cancelUrl());
        body.put("notif_url", properties.notifUrl());

        return orangeWebClient.post()
                .uri("/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("payment_url"))
                .doOnSuccess(url -> log.info("Orange Money payment initiated, redirectUrl={}", url));
    }
}
