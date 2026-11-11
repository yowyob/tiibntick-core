package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.ProxyKernelAuthUseCase;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IKernelAuthGatewayPort;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelAuthResult;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelRawResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

/**
 * Implements {@link ProxyKernelAuthUseCase} — contains NO authentication logic itself,
 * all cryptography/credential-checking stays on the Kernel. This service is a thin
 * pass-through to {@link IKernelAuthGatewayPort}; envelope-to-{@code ApiResponse}
 * translation lives on {@code KernelApiEnvelope.toApiResponse()} so both this service
 * and the controllers can reuse it without duplicating the mapping.
 *
 * @author MANFOUO Braun
 */
public class KernelAuthGatewayService implements ProxyKernelAuthUseCase {

    private final IKernelAuthGatewayPort kernelAuthGatewayPort;
    /** Optional: tells higher-layer modules that a user just obtained an access token. */
    private final AuthenticatedSessionPublisher sessionPublisher;

    public KernelAuthGatewayService(IKernelAuthGatewayPort kernelAuthGatewayPort) {
        this(kernelAuthGatewayPort, null);
    }

    public KernelAuthGatewayService(IKernelAuthGatewayPort kernelAuthGatewayPort,
                                    AuthenticatedSessionPublisher sessionPublisher) {
        this.kernelAuthGatewayPort = kernelAuthGatewayPort;
        this.sessionPublisher = sessionPublisher;
    }

    @Override
    public Mono<KernelAuthResult> callAuth(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization) {
        return kernelAuthGatewayPort.invokeAuth(method, kernelPath, body, bearerAuthorization)
                .doOnNext(this::announceIssuedToken);
    }

    /**
     * Any successful Kernel auth answer that carries an {@code accessToken} (login, MFA
     * confirmation, context selection, refresh, sign-up) means a user now holds a session:
     * let listeners prepare it (wallet…) in the background. Never affects the response.
     */
    private void announceIssuedToken(KernelAuthResult result) {
        if (sessionPublisher == null || result == null || result.httpStatus() < 200 || result.httpStatus() >= 300
                || result.envelope() == null || result.envelope().data() == null) {
            return;
        }
        JsonNode token = result.envelope().data().get("accessToken");
        if (token != null && token.isTextual()) {
            sessionPublisher.tokenIssued(token.asText());
        }
    }

    @Override
    public Mono<KernelRawResponse> callOidc(HttpMethod method, String kernelPath, MediaType contentType, byte[] body, String bearerAuthorization) {
        return kernelAuthGatewayPort.invokeOidc(method, kernelPath, contentType, body, bearerAuthorization);
    }
}
