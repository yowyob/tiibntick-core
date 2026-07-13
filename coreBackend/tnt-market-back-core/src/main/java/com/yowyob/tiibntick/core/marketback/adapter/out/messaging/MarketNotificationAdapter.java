package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketNotificationPort;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Notification adapter for market events — delegates to tnt-notify-core's
 * {@link ISendNotificationUseCase}. Ported from the standalone
 * tiibntick-market-backend's {@code NotificationClientAdapter}, which was a
 * logging-only stub; now wired to the real notification pipeline.
 *
 * <p>Uses the {@code IN_APP_WEBSOCKET} channel with the recipient's actor id
 * as the target destination, since this module has no phone/email/FCM token
 * lookup of its own — tnt-notify-core resolves per-user channel preferences
 * server-side via {@code IManageNotificationPreferencesUseCase} on delivery.
 * Notification failures are logged and swallowed ({@code onErrorResume}) so a
 * transient notify-core outage never fails the calling market use case.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketNotificationAdapter implements IMarketNotificationPort {

    private final ISendNotificationUseCase sendNotificationUseCase;

    @Override
    public Mono<Void> notifyClientQuoteReceived(String tenantId, UUID clientId, String quoteRequestId, String providerName) {
        return sendNotification(tenantId, clientId, "CLIENT_QUOTE_RECEIVED",
                Map.of("quoteRequestId", quoteRequestId, "providerName", providerName));
    }

    @Override
    public Mono<Void> notifyProviderNewQuoteRequest(String tenantId, UUID providerId, String quoteRequestId, String clientCity) {
        return sendNotification(tenantId, providerId, "PROVIDER_NEW_QUOTE_REQUEST",
                Map.of("quoteRequestId", quoteRequestId, "clientCity", clientCity));
    }

    @Override
    public Mono<Void> notifyOrderConfirmed(String tenantId, UUID clientId, String orderId, String providerName) {
        return sendNotification(tenantId, clientId, "ORDER_CONFIRMED",
                Map.of("orderId", orderId, "providerName", providerName));
    }

    @Override
    public Mono<Void> notifyOrderPaid(String tenantId, UUID providerId, String orderId, long amountXaf) {
        return sendNotification(tenantId, providerId, "ORDER_PAID",
                Map.of("orderId", orderId, "amountXaf", String.valueOf(amountXaf)));
    }

    @Override
    public Mono<Void> notifyOrderCompleted(String tenantId, UUID clientId, String orderId) {
        return sendNotification(tenantId, clientId, "ORDER_COMPLETED", Map.of("orderId", orderId));
    }

    @Override
    public Mono<Void> notifyReviewPublished(String tenantId, UUID providerId, String reviewId, double rating) {
        return sendNotification(tenantId, providerId, "REVIEW_PUBLISHED",
                Map.of("reviewId", reviewId, "rating", String.valueOf(rating)));
    }

    @Override
    public Mono<Void> notifyListingApproved(String tenantId, UUID providerId, String listingId) {
        return sendNotification(tenantId, providerId, "LISTING_APPROVED", Map.of("listingId", listingId));
    }

    @Override
    public Mono<Void> notifyListingRejected(String tenantId, UUID providerId, String listingId, String reason) {
        return sendNotification(tenantId, providerId, "LISTING_REJECTED",
                Map.of("listingId", listingId, "reason", reason));
    }

    private Mono<Void> sendNotification(String tenantId, UUID recipientId, String templateKey, Map<String, String> params) {
        NotificationModel model = NotificationModel.of(templateKey, "fr", Map.copyOf(params));
        return sendNotificationUseCase.send(
                        tenantId,
                        null,
                        recipientId.toString(),
                        recipientId.toString(),
                        model,
                        NotificationChannel.IN_APP_WEBSOCKET)
                .doOnError(e -> log.warn("Failed to send notification {} to {}: {}", templateKey, recipientId, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
