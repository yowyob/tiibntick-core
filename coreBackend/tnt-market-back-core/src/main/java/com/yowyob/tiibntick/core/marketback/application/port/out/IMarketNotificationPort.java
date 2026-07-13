package com.yowyob.tiibntick.core.marketback.application.port.out;

import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port — sends notifications via tnt-notify-core.
 * @author MANFOUO Braun
 */
public interface IMarketNotificationPort {

    Mono<Void> notifyClientQuoteReceived(String tenantId, UUID clientId, String quoteRequestId, String providerName);
    Mono<Void> notifyProviderNewQuoteRequest(String tenantId, UUID providerId, String quoteRequestId, String clientCity);
    Mono<Void> notifyOrderConfirmed(String tenantId, UUID clientId, String orderId, String providerName);
    Mono<Void> notifyOrderPaid(String tenantId, UUID providerId, String orderId, long amountXaf);
    Mono<Void> notifyOrderCompleted(String tenantId, UUID clientId, String orderId);
    Mono<Void> notifyReviewPublished(String tenantId, UUID providerId, String reviewId, double rating);
    Mono<Void> notifyListingApproved(String tenantId, UUID providerId, String listingId);
    Mono<Void> notifyListingRejected(String tenantId, UUID providerId, String listingId, String reason);
}
