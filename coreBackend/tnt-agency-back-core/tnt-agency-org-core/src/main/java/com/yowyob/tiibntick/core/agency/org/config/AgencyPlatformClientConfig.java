package com.yowyob.tiibntick.core.agency.org.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * HTTP client for platform cores (delivery, inventory, billing, resource, onboarding gateway).
 * Consumed by agency-back-core modules — never by the BFF directly.
 *
 * <p>Forwards the inbound user JWT so Kernel phase-1 (candidate BusinessActor) binds to the
 * correct {@code sub}. Phase-2 approve also benefits when an admin JWT is present.
 */
@Configuration
public class AgencyPlatformClientConfig {

    @Value("${tnt.agency.platform.base-url:${tnt.core.base-url:http://localhost:8080}}")
    private String platformBaseUrl;

    @Value("${tnt.agency.platform.api-key:${tnt.core.api-key:}}")
    private String apiKey;

    @Value("${tnt.agency.platform.client-id:${tnt.core.client-id:agency-back-core}}")
    private String clientId;

    @Bean("agencyPlatformWebClient")
    public WebClient agencyPlatformWebClient(WebClient.Builder builder) {
        WebClient.Builder configured = builder
                .clone()
                .baseUrl(normalizeBaseUrl(platformBaseUrl))
                .filter(propagateBearerToken());
        if (apiKey != null && !apiKey.isBlank()) {
            configured = configured.defaultHeader("X-Api-Key", apiKey);
        }
        if (clientId != null && !clientId.isBlank()) {
            configured = configured.defaultHeader("X-Client-Id", clientId);
        }
        return configured.build();
    }

    private static ExchangeFilterFunction propagateBearerToken() {
        return (request, next) -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> auth.getToken().getTokenValue())
                .map(token -> ClientRequest.from(request)
                        .headers(h -> h.setBearerAuth(token))
                        .build())
                .defaultIfEmpty(request)
                .flatMap(next::exchange);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
