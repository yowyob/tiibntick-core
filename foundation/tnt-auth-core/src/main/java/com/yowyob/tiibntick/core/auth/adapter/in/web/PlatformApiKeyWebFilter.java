package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;
import com.yowyob.tiibntick.core.auth.application.service.PlatformClientRegistry;
import com.yowyob.tiibntick.core.auth.domain.model.PlatformClientApplication;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Validates {@code X-Client-Id}/{@code X-Api-Key} for every request on the platform
 * gateway's own {@link org.springframework.security.web.server.SecurityWebFilterChain}
 * (see {@code TntAuthGatewaySecurityConfig}, {@code @Order(10)}) — the mechanism that lets
 * TiiBnTick Core tell which platform backend (Agency, Go, ...) is calling, distinct from
 * any end-user JWT the request may also carry.
 *
 * <p>Never validates a Kernel credential — Kernel's {@code X-Api-Key}/{@code X-Client-Id}
 * stay server-side only (see {@code KernelBridgeConfig}); this filter checks the
 * platform's OWN credential pair against {@link PlatformClientRegistry}.
 *
 * @author MANFOUO Braun
 */
public class PlatformApiKeyWebFilter implements WebFilter {

    public static final String PLATFORM_CLIENT_ATTRIBUTE = "tnt.platform.client";

    private final PlatformClientRegistry registry;
    private final ObjectMapper objectMapper;

    public PlatformApiKeyWebFilter(PlatformClientRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");

        return registry.authenticate(clientId, apiKey)
                .map(client -> proceed(exchange, chain, client))
                .orElseGet(() -> reject(exchange));
    }

    private Mono<Void> proceed(ServerWebExchange exchange, WebFilterChain chain, PlatformClientApplication client) {
        exchange.getAttributes().put(PLATFORM_CLIENT_ATTRIBUTE, client);
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorDetail.of("PLATFORM_UNAUTHORIZED", "Missing or invalid X-Client-Id / X-Api-Key"), null);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"status\":\"ERROR\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(bytes)));
    }
}
