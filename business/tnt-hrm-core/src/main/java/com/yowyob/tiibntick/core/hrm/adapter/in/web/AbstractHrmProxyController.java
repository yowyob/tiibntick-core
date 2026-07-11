package com.yowyob.tiibntick.core.hrm.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.hrm.application.port.in.ProxyKernelHrmUseCase;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Shared proxy helper for the 16 {@code Hrm*Controller} classes (one per Kernel HRM
 * controller) — forwards the caller's query parameters unchanged and translates the
 * Kernel's envelope into TiiBnTick Core's own {@link ApiResponse}, so none of the 160
 * endpoint methods has to repeat this wiring.
 *
 * <p>Request/response bodies are forwarded as opaque JSON rather than re-declared as
 * one DTO per Kernel HRM operation — see {@code KernelApiEnvelope}'s javadoc for why.
 *
 * @author MANFOUO Braun
 */
public abstract class AbstractHrmProxyController {

    protected final ProxyKernelHrmUseCase useCase;

    protected AbstractHrmProxyController(ProxyKernelHrmUseCase useCase) {
        this.useCase = useCase;
    }

    protected Mono<ResponseEntity<ApiResponse<JsonNode>>> proxy(
            HttpMethod method, String kernelPath, ServerWebExchange exchange, JsonNode body, String authorization) {
        return useCase.call(method, kernelPath, exchange.getRequest().getQueryParams(), body, authorization)
                .map(result -> ResponseEntity.status(result.httpStatus()).body(result.envelope().toApiResponse()));
    }
}
