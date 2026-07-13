package com.yowyob.tiibntick.core.agency.org.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP client for platform cores (delivery, inventory, billing, resource).
 * Consumed by agency-back-core modules — never by the BFF directly.
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
        WebClient.Builder configured = builder.baseUrl(normalizeBaseUrl(platformBaseUrl));
        if (apiKey != null && !apiKey.isBlank()) {
            configured = configured.defaultHeader("X-Api-Key", apiKey);
        }
        if (clientId != null && !clientId.isBlank()) {
            configured = configured.defaultHeader("X-Client-Id", clientId);
        }
        return configured.build();
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
