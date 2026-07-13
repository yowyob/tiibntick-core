package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command — place a MarketOrder from an already-accepted QuoteRequest (i.e. one on which
 * {@code selectQuoteResponse} was already called). Provider, listing, delivery request and
 * pricing are all derived server-side from the QuoteRequest aggregate and its selected
 * QuoteResponse — see {@code MarketOrder#fromQuote}.
 *
 * @author MANFOUO Braun
 */
public record PlaceOrderFromQuoteCommand(
        @NotNull String tenantId,
        @NotNull UUID quoteRequestId,
        @NotNull UUID clientId
) {}
