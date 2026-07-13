package com.yowyob.tiibntick.core.marketback.domain.service;

import com.yowyob.tiibntick.core.marketback.domain.model.*;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Domain service — quote negotiation business rules.
 *
 * <p>Encapsulates logic that spans multiple aggregates or does not
 * naturally belong to a single aggregate root.</p>
 *
 * @author MANFOUO Braun
 */
public class QuoteNegotiationService {

    /**
     * Returns the best response for a quote request based on a scoring
     * function that combines price and estimated delivery time.
     *
     * @param quoteRequest the quote request
     * @return the best quote response, if any
     */
    public Optional<QuoteResponse> selectBestOffer(QuoteRequest quoteRequest) {
        List<QuoteResponse> pending = quoteRequest.getResponses().stream()
                .filter(r -> r.getStatus() == QuoteResponseStatus.PENDING)
                .toList();

        if (pending.isEmpty()) return Optional.empty();

        return pending.stream()
                .min(Comparator.comparingDouble(this::score));
    }

    /**
     * Lower is better: price-weighted + time penalty.
     */
    private double score(QuoteResponse r) {
        double priceScore = (double) r.getProposedPrice().amount();
        long hoursUntilDelivery = r.getEstimatedDeliveryAt() != null
                ? java.time.Duration.between(
                java.time.LocalDateTime.now(), r.getEstimatedDeliveryAt()).toHours()
                : Long.MAX_VALUE;
        return priceScore + hoursUntilDelivery * 0.5;
    }

    /**
     * Validates that a provider is allowed to respond to the given quote request.
     */
    public void assertProviderMayRespond(QuoteRequest quoteRequest, java.util.UUID providerId) {
        if (!quoteRequest.getProviderId().equals(providerId)) {
            throw new MarketDomainException(
                    "Provider " + providerId + " was not invited to respond to this quote.");
        }
        if (quoteRequest.getStatus() != QuoteStatus.PENDING
                && quoteRequest.getStatus() != QuoteStatus.RESPONDED) {
            throw new MarketDomainException("Quote request is not open for responses.");
        }
    }
}
