package com.yowyob.tiibntick.core.legacydocuments.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.legacydocuments.application.port.in.ProxyKernelLegacyDocumentUseCase;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Shared proxy helper for the 7 document-type controllers (one per Kernel
 * {@code billing-legacy-documents-controller} document family) — forwards the caller's
 * query parameters unchanged and translates the Kernel's envelope into TiiBnTick Core's
 * own {@link ApiResponse}, so none of the 64 endpoint methods has to repeat this wiring.
 *
 * <p>Request/response bodies are forwarded as opaque JSON rather than re-declared as one
 * DTO per operation — see {@code KernelApiEnvelope}'s javadoc for why (same rationale as
 * {@code tnt-hrm-core}'s {@code AbstractHrmProxyController}, whose codegen technique this
 * module reuses).
 *
 * @author MANFOUO Braun
 */
public abstract class AbstractLegacyDocumentProxyController {

    protected final ProxyKernelLegacyDocumentUseCase useCase;

    protected AbstractLegacyDocumentProxyController(ProxyKernelLegacyDocumentUseCase useCase) {
        this.useCase = useCase;
    }

    protected Mono<ResponseEntity<ApiResponse<JsonNode>>> proxy(
            HttpMethod method, String kernelPath, ServerWebExchange exchange, JsonNode body, String authorization) {
        return useCase.call(method, kernelPath, exchange.getRequest().getQueryParams(), body, authorization)
                .map(result -> ResponseEntity.status(result.httpStatus()).body(result.envelope().toApiResponse()));
    }
}
