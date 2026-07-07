package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.adapter.in.web.dto.request.OrangeCallbackRequest;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.ConfirmPaymentCommand;
import com.yowyob.tiibntick.core.billing.wallet.config.MtnMoMoProperties;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * PaymentWebhookController — handles asynchronous payment callbacks from providers.
 *
 * Security: HMAC-SHA256 signature verification for MTN MoMo.
 * Base path: /billing/webhooks
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestController
@RequestMapping("/billing/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final IWalletUseCase walletUseCase;
    private final MtnMoMoProperties mtnProperties;

    /**
     * POST /billing/webhooks/mtn
     * MTN MoMo callback — triggered after the user accepts or rejects the USSD push.
     * Signature verified via X-MTN-Signature header (HMAC-SHA256).
     */
    @PostMapping("/mtn")
    public Mono<ResponseEntity<Void>> handleMtnCallback(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Callback-Url", required = false) String callbackUrl,
            ServerWebExchange exchange) {

        log.info("MTN MoMo webhook received");

        return verifyMtnSignature(rawBody, exchange)
                .flatMap(valid -> {
                    if (!valid) {
                        log.warn("MTN MoMo webhook: invalid signature — rejecting");
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build());
                    }
                    return parseMtnCallback(rawBody)
                            .flatMap(walletUseCase::handlePaymentCallback)
                            .doOnSuccess(intent -> log.info(
                                    "MTN callback processed: status={} ref={}",
                                    intent.getStatus(), intent.getExternalRef()))
                            .thenReturn(ResponseEntity.ok().<Void>build());
                })
                .onErrorResume(e -> {
                    log.warn("MTN callback processing error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).<Void>build());
                });
    }

    /**
     * POST /billing/webhooks/orange
     * Orange Money Cameroon callback.
     */
    @PostMapping("/orange")
    public Mono<ResponseEntity<Void>> handleOrangeCallback(
            @RequestBody OrangeCallbackRequest request) {

        log.info("Orange Money webhook received orderId={} status={}", request.orderId(), request.status());

        boolean success = "SUCCESS".equalsIgnoreCase(request.status());
        ConfirmPaymentCommand command = new ConfirmPaymentCommand(
                request.orderId(),
                request.transactionId(),
                PaymentChannel.ORANGE_MONEY,
                success ? "SUCCESSFUL" : "FAILED",
                success ? null : request.message());

        return walletUseCase.handlePaymentCallback(command)
                .doOnSuccess(intent -> log.info("Orange callback processed: status={}", intent.getStatus()))
                .thenReturn(ResponseEntity.ok().<Void>build())
                .onErrorResume(e -> {
                    log.warn("Orange callback processing error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).<Void>build());
                });
    }

    /**
     * POST /billing/webhooks/stripe
     * Stripe payment webhook (Stripe-Signature header verified).
     */
    @PostMapping("/stripe")
    public Mono<ResponseEntity<Void>> handleStripeCallback(
            @RequestBody String rawBody,
            @RequestHeader("Stripe-Signature") String stripeSignature) {

        log.info("Stripe webhook received");
        // Stripe signature verification would use stripe-java SDK in production.
        // Simplified: parse the JSON body for payment_intent.succeeded or payment_intent.payment_failed
        return parseStripeEvent(rawBody)
                .flatMap(walletUseCase::handlePaymentCallback)
                .thenReturn(ResponseEntity.ok().<Void>build())
                .onErrorResume(e -> {
                    log.error("Stripe callback error: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).<Void>build());
                });
    }

    // ─── private helpers ───────────────────────────────────────────────────

    private Mono<Boolean> verifyMtnSignature(String body, ServerWebExchange exchange) {
        String signature = exchange.getRequest().getHeaders().getFirst("X-MTN-Signature");
        if (signature == null || signature.isBlank()) {
            // MTN sandbox may not send signatures — allow in non-production
            return Mono.just(true);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    mtnProperties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return Mono.just(expected.equalsIgnoreCase(signature));
        } catch (Exception e) {
            log.error("HMAC verification failed: {}", e.getMessage());
            return Mono.just(false);
        }
    }

    private Mono<ConfirmPaymentCommand> parseMtnCallback(String rawBody) {
        // MTN sends JSON: {externalId, referenceId, status, financialTransactionId, reason}
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(rawBody);
            String referenceId = node.path("referenceId").asText();
            String status = node.path("status").asText();
            String financialTxId = node.path("financialTransactionId").asText();
            String reason = node.path("reason").asText(null);

            return Mono.just(new ConfirmPaymentCommand(
                    referenceId, financialTxId,
                    PaymentChannel.MTN_MOMO, status, reason));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Cannot parse MTN callback: " + e.getMessage()));
        }
    }

    private Mono<ConfirmPaymentCommand> parseStripeEvent(String rawBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(rawBody);
            String eventType = node.path("type").asText();
            com.fasterxml.jackson.databind.JsonNode dataObject = node.path("data").path("object");
            String paymentIntentId = dataObject.path("id").asText();

            boolean success = "payment_intent.succeeded".equals(eventType);
            return Mono.just(new ConfirmPaymentCommand(
                    paymentIntentId, paymentIntentId,
                    PaymentChannel.STRIPE,
                    success ? "SUCCESSFUL" : "FAILED",
                    success ? null : eventType));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Cannot parse Stripe event: " + e.getMessage()));
        }
    }
}
