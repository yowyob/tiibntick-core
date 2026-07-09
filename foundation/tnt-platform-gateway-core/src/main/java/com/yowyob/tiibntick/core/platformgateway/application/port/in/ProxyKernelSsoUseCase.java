package com.yowyob.tiibntick.core.platformgateway.application.port.in;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ResolveSsoContextRequest;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ResolveSsoContextResponse;
import com.yowyob.tiibntick.core.platformgateway.domain.model.SsoLaunchRequest;
import com.yowyob.tiibntick.core.platformgateway.domain.model.SsoLaunchResponse;
import com.yowyob.tiibntick.core.platformgateway.domain.model.SsoTokenExchangeRequest;
import com.yowyob.tiibntick.core.platformgateway.domain.model.SsoTokenExchangeResponse;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) use-case: YowYob SSO handshake for platform backends, orchestrated
 * against the Kernel's {@code auth-oidc-controller}. Unlike {@link ProxyKernelAuthUseCase},
 * this is real orchestration logic (interpreting the userinfo payload, building the
 * token-exchange grant), not a raw proxy — so the contracts are typed rather than opaque JSON.
 *
 * <p>Implemented by {@code KernelSsoGatewayService}, exposed by {@code PlatformSsoController}.
 *
 * @author MANFOUO Braun
 */
public interface ProxyKernelSsoUseCase {

    /**
     * Resolves the SSO {@code contextId} for a given Kernel organization by calling
     * {@code GET /oauth2/userinfo} with the shared session token and matching
     * {@code kernelOrganizationId} against the returned {@code contexts[].organizations[]}.
     *
     * @param bearerAuthorization the caller's {@code Authorization} header, preferred over
     *                            {@code request.sharedSessionToken()} when both are present
     */
    Mono<ResolveSsoContextResponse> resolveContext(ResolveSsoContextRequest request, String bearerAuthorization);

    /**
     * Exchanges a resolved SSO context for a service-scoped access token via
     * {@code POST /oauth2/token} (grant_type=token-exchange).
     */
    Mono<SsoTokenExchangeResponse> exchangeToken(SsoTokenExchangeRequest request);

    /**
     * Composite convenience: resolves the context then exchanges the token, and builds the
     * app's SSO redirect URL (see {@code tnt.platform-gateway.sso-app-redirect-urls}).
     */
    Mono<SsoLaunchResponse> launch(SsoLaunchRequest request);
}
