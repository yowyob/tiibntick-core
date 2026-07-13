package com.yowyob.tiibntick.core.marketback.application.port.in;

import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Inbound port — QuoteRequest use cases (client and provider sides).
 * @author MANFOUO Braun
 */
public interface IManageQuoteRequestUseCase {

    Mono<QuoteRequestResponse> createQuoteRequest(CreateQuoteRequestCommand command);
    Mono<QuoteRequestResponse> submitQuoteResponse(UUID quoteRequestId, SubmitQuoteResponseCommand command, String tenantId);
    Mono<QuoteRequestResponse> selectQuoteResponse(UUID quoteRequestId, UUID responseId, UUID clientId, String tenantId);
    Mono<QuoteRequestResponse> rejectQuoteResponse(UUID quoteRequestId, UUID responseId, UUID clientId, String tenantId);
    Mono<QuoteRequestResponse> cancelQuoteRequest(UUID quoteRequestId, String reason, UUID clientId, String tenantId);
    Mono<QuoteRequestResponse> getQuoteRequest(UUID quoteRequestId, String tenantId);
    Flux<QuoteRequestResponse> getClientQuoteRequests(UUID clientId, String tenantId);
    Flux<QuoteRequestResponse> getProviderLeads(UUID providerId, String tenantId);
}
