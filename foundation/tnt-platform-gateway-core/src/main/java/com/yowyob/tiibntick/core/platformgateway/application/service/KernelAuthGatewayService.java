package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.fasterxml.jackson.databind.JsonNode;
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

    public KernelAuthGatewayService(IKernelAuthGatewayPort kernelAuthGatewayPort) {
        this.kernelAuthGatewayPort = kernelAuthGatewayPort;
    }

    @Override
    public Mono<KernelAuthResult> callAuth(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization) {
        return kernelAuthGatewayPort.invokeAuth(method, kernelPath, body, bearerAuthorization);
    }

    @Override
    public Mono<KernelRawResponse> callOidc(HttpMethod method, String kernelPath, MediaType contentType, byte[] body, String bearerAuthorization) {
        return kernelAuthGatewayPort.invokeOidc(method, kernelPath, contentType, body, bearerAuthorization);
    }
}
