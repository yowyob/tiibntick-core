package com.yowyob.tiibntick.core.platformgateway.application.port.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelAuthResult;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelRawResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) use-case: lets platform backends (Agency, Go, Link, Market,
 * Point Relais, ...) authenticate and manage their sessions by talking to TiiBnTick
 * Core only — never to the Kernel directly.
 *
 * <p>Implemented by {@code KernelAuthGatewayService}, exposed by
 * {@code PlatformAuthController} / {@code PlatformAuthOidcController}.
 *
 * @author MANFOUO Braun
 */
public interface ProxyKernelAuthUseCase {

    /**
     * Executes one Kernel {@code auth-controller} operation. Returns the real Kernel
     * HTTP status alongside the envelope so the controller can respond with it
     * (401/403/409/...) instead of flattening every outcome to 200.
     */
    Mono<KernelAuthResult> callAuth(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization);

    /**
     * Executes one Kernel {@code auth-oidc-controller} operation, passed through
     * byte-for-byte (standard OAuth2/OIDC JSON — no TiiBnTick envelope).
     */
    Mono<KernelRawResponse> callOidc(HttpMethod method, String kernelPath, MediaType contentType, byte[] body, String bearerAuthorization);
}
