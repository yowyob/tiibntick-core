package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command — reject a single provider {@code QuoteResponse} within a QuoteRequest, without
 * cancelling the whole request (see {@code CancelQuoteRequestCommand}-equivalent flow for that).
 *
 * @author MANFOUO Braun
 */
public record RejectQuoteResponseCommand(
        @NotNull String tenantId,
        @NotNull UUID quoteRequestId,
        @NotNull UUID responseId,
        @NotNull UUID clientId
) {}
