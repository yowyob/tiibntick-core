package com.yowyob.tiibntick.core.auth.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.auth.application.port.out.IKernelAuthGatewayPort;
import com.yowyob.tiibntick.core.auth.domain.model.KernelApiEnvelope;
import com.yowyob.tiibntick.core.auth.domain.model.KernelAuthResult;
import com.yowyob.tiibntick.core.auth.domain.model.KernelRawResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the Kernel's authentication HTTP surface via the shared {@code kernelWebClient}
 * bean (defined once in {@code tnt-bootstrap}'s {@code KernelBridgeConfig} — carries the
 * Core's own {@code X-Api-Key}/{@code X-Client-Id}/{@code X-Solution-Code} identity).
 *
 * <p>Never injects a Kernel Spring bean/type — only the generic {@link WebClient} (see
 * root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public class KernelAuthGatewayAdapter implements IKernelAuthGatewayPort {

    private final WebClient kernelWebClient;

    public KernelAuthGatewayAdapter(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelAuthResult> invokeAuth(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization) {
        WebClient.RequestBodySpec spec = kernelWebClient.method(method)
                .uri(kernelPath)
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
                .map(envelope -> new KernelAuthResult(response.statusCode().value(), envelope))
                .onErrorResume(ex -> Mono.just(new KernelAuthResult(
                        response.statusCode().value(),
                        new KernelApiEnvelope(false, null,
                                "Kernel returned a non-JSON or unexpected response body",
                                "KERNEL_HTTP_" + response.statusCode().value(), null)))));
    }

    @Override
    public Mono<KernelRawResponse> invokeOidc(HttpMethod method, String kernelPath, MediaType contentType, byte[] body, String bearerAuthorization) {
        WebClient.RequestBodySpec spec = kernelWebClient.method(method).uri(kernelPath);
        if (contentType != null) {
            spec = spec.contentType(contentType);
        }
        if (bearerAuthorization != null && !bearerAuthorization.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, bearerAuthorization);
        }
        WebClient.RequestHeadersSpec<?> withBody = (body != null && body.length > 0)
                ? spec.body(BodyInserters.fromValue(body))
                : spec;
        return withBody.exchangeToMono(response -> response.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(bytes -> new KernelRawResponse(
                        response.statusCode().value(),
                        response.headers().contentType().orElse(MediaType.APPLICATION_JSON),
                        bytes)));
    }
}
