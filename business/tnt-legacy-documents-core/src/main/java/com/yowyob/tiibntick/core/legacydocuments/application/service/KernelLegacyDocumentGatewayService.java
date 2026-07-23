package com.yowyob.tiibntick.core.legacydocuments.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.legacydocuments.application.port.in.ProxyKernelLegacyDocumentUseCase;
import com.yowyob.tiibntick.core.legacydocuments.application.port.out.IKernelLegacyDocumentGatewayPort;
import com.yowyob.tiibntick.core.legacydocuments.domain.model.KernelDocumentResult;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

/**
 * Implements {@link ProxyKernelLegacyDocumentUseCase} — contains NO commercial-document
 * business logic itself (numbering, approval workflow, accounting sync rules, ...): all of
 * that stays on the Kernel. This service is a thin pass-through to
 * {@link IKernelLegacyDocumentGatewayPort}.
 *
 * @author MANFOUO Braun
 */
@Service
public class KernelLegacyDocumentGatewayService implements ProxyKernelLegacyDocumentUseCase {

    private final IKernelLegacyDocumentGatewayPort kernelLegacyDocumentGatewayPort;

    public KernelLegacyDocumentGatewayService(IKernelLegacyDocumentGatewayPort kernelLegacyDocumentGatewayPort) {
        this.kernelLegacyDocumentGatewayPort = kernelLegacyDocumentGatewayPort;
    }

    @Override
    public Mono<KernelDocumentResult> call(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                            JsonNode body, String bearerAuthorization) {
        return kernelLegacyDocumentGatewayPort.invoke(method, kernelPath, queryParams, body, bearerAuthorization);
    }
}
