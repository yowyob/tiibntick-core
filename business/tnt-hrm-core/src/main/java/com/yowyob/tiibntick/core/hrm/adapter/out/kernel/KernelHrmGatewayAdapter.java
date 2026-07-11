package com.yowyob.tiibntick.core.hrm.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.hrm.application.port.out.IKernelHrmGatewayPort;
import com.yowyob.tiibntick.core.hrm.domain.model.KernelApiEnvelope;
import com.yowyob.tiibntick.core.hrm.domain.model.KernelHrmResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the Kernel's HRM HTTP surface via the shared {@code kernelWebClient} bean
 * (defined once in {@code tnt-bootstrap}'s {@code KernelBridgeConfig} — carries the
 * Core's own {@code X-Api-Key}/{@code X-Client-Id}/{@code X-Solution-Code} identity).
 *
 * <p>Never injects a Kernel Spring bean/type — only the generic {@link WebClient} (see
 * root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelHrmGatewayAdapter implements IKernelHrmGatewayPort {

    private final WebClient kernelWebClient;

    public KernelHrmGatewayAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelHrmResult> invoke(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                         JsonNode body, String bearerAuthorization) {
        WebClient.RequestBodySpec spec = kernelWebClient.method(method)
                .uri(uriBuilder -> uriBuilder.path(kernelPath).queryParams(queryParams).build())
                .contentType(MediaType.APPLICATION_JSON);
        if (bearerAuthorization != null && !bearerAuthorization.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, bearerAuthorization);
        }
        WebClient.RequestHeadersSpec<?> withBody = body != null
                ? spec.body(BodyInserters.fromValue(body))
                : spec;
        return withBody.exchangeToMono(response -> response.bodyToMono(KernelApiEnvelope.class)
                .defaultIfEmpty(new KernelApiEnvelope(
                        response.statusCode().is2xxSuccessful(), null, null, null, null))
                .map(envelope -> new KernelHrmResult(response.statusCode().value(), envelope))
                .onErrorResume(ex -> Mono.just(new KernelHrmResult(
                        response.statusCode().value(),
                        new KernelApiEnvelope(false, null,
                                "Kernel returned a non-JSON or unexpected response body",
                                "KERNEL_HTTP_" + response.statusCode().value(), null)))));
    }
}
