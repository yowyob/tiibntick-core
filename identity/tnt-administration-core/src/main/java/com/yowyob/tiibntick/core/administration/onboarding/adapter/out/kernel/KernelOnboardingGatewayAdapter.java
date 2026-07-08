package com.yowyob.tiibntick.core.administration.onboarding.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.administration.onboarding.application.port.out.IKernelOnboardingGatewayPort;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.KernelEnvelope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the Kernel's organization/administration/actor HTTP surface via the shared
 * {@code kernelWebClient} bean (defined once in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig}). Never injects a Kernel Spring bean/type — only the
 * generic {@link WebClient} (see root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelOnboardingGatewayAdapter implements IKernelOnboardingGatewayPort {

    private final WebClient kernelWebClient;

    public KernelOnboardingGatewayAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelEnvelope> invoke(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization) {
        WebClient.RequestBodySpec spec = kernelWebClient.method(method)
                .uri(kernelPath)
                .contentType(MediaType.APPLICATION_JSON);
        if (bearerAuthorization != null && !bearerAuthorization.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, bearerAuthorization);
        }
        WebClient.RequestHeadersSpec<?> withBody = body != null
                ? spec.body(BodyInserters.fromValue(body))
                : spec;
        return withBody.exchangeToMono(response -> response.bodyToMono(KernelEnvelope.class)
                .defaultIfEmpty(new KernelEnvelope(response.statusCode().is2xxSuccessful(), null, null, null, null))
                .onErrorResume(ex -> Mono.just(new KernelEnvelope(false, null,
                        "Kernel returned a non-JSON or unexpected response body",
                        "KERNEL_HTTP_" + response.statusCode().value(), null))));
    }
}
