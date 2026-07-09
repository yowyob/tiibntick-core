package com.yowyob.tiibntick.core.platformgateway.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelAuthResult;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelRawResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

/**
 * Secondary (outbound) port: calls the Kernel's authentication HTTP surface
 * ({@code auth-controller}, {@code auth-oidc-controller}) — see
 * {@code docs/kernel-api/endpoints.md} for the full catalogue.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.platformgateway.adapter.out.kernel.KernelAuthGatewayAdapter}
 * using the shared {@code kernelWebClient} bean — never a Kernel Spring bean/type
 * (see root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public interface IKernelAuthGatewayPort {

    /**
     * Calls one of the Kernel's {@code auth-controller} endpoints (JSON body in/out,
     * wrapped in the Kernel's own {@code ApiResponse} envelope).
     *
     * @param method            HTTP method of the Kernel endpoint
     * @param kernelPath        Kernel-relative path, e.g. {@code /api/auth/login}
     * @param body              request body to forward as-is, or {@code null} when the
     *                          Kernel endpoint takes none
     * @param bearerAuthorization the caller's raw {@code Authorization} header value
     *                          (e.g. {@code "Bearer eyJ..."}) to forward, or {@code null}
     *                          when the platform backend sent none
     */
    Mono<KernelAuthResult> invokeAuth(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization);

    /**
     * Calls one of the Kernel's {@code auth-oidc-controller} endpoints (OIDC discovery /
     * OAuth2 token / introspect / userinfo) — raw passthrough, no envelope, since these
     * are standard OAuth2/OIDC responses that must reach the caller unmodified.
     *
     * @param method               HTTP method of the Kernel endpoint
     * @param kernelPath           Kernel-relative path, e.g. {@code /oauth2/token}
     * @param contentType          content type of {@code body}, or {@code null} for a bodyless call
     * @param body                 raw request body bytes to forward as-is, or {@code null}
     * @param bearerAuthorization  the caller's raw {@code Authorization} header value to forward,
     *                             or {@code null}
     */
    Mono<KernelRawResponse> invokeOidc(HttpMethod method, String kernelPath, MediaType contentType, byte[] body, String bearerAuthorization);
}
