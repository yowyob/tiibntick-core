package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteRequestId;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteResponseId;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when a provider submits a quote response. @author MANFOUO Braun */
public record QuoteResponseSubmittedEvent(
        QuoteRequestId quoteRequestId, QuoteResponseId responseId,
        UUID providerId, Money proposedPrice, LocalDateTime occurredAt) implements MarketDomainEvent {
    // Keyed by the parent QuoteRequest, not the response sub-entity — matches the
    // MARKET_QUOTE_REQUEST aggregate type entity_version is indexed under (there is no
    // separate MARKET_QUOTE_RESPONSE aggregate type).
    @Override
    public String aggregateId() { return quoteRequestId.toString(); }
}
