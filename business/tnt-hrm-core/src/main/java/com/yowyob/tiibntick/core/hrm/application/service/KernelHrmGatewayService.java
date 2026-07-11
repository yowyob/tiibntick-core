package com.yowyob.tiibntick.core.hrm.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.hrm.application.port.in.ProxyKernelHrmUseCase;
import com.yowyob.tiibntick.core.hrm.application.port.out.IKernelHrmGatewayPort;
import com.yowyob.tiibntick.core.hrm.domain.model.KernelHrmResult;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

/**
 * Implements {@link ProxyKernelHrmUseCase} — contains NO HRM business logic itself
 * (employee lifecycle rules, leave accrual, payroll, ...): all of that stays on the
 * Kernel. This service is a thin pass-through to {@link IKernelHrmGatewayPort}.
 *
 * @author MANFOUO Braun
 */
@Service
public class KernelHrmGatewayService implements ProxyKernelHrmUseCase {

    private final IKernelHrmGatewayPort kernelHrmGatewayPort;

    public KernelHrmGatewayService(IKernelHrmGatewayPort kernelHrmGatewayPort) {
        this.kernelHrmGatewayPort = kernelHrmGatewayPort;
    }

    @Override
    public Mono<KernelHrmResult> call(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                       JsonNode body, String bearerAuthorization) {
        return kernelHrmGatewayPort.invoke(method, kernelPath, queryParams, body, bearerAuthorization);
    }
}
