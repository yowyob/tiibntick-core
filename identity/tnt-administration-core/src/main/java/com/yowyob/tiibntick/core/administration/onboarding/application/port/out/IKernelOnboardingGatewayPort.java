package com.yowyob.tiibntick.core.administration.onboarding.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.KernelEnvelope;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

/**
 * Secondary (outbound) port: calls the Kernel's {@code organization-controller} /
 * {@code administration-controller} / {@code actor-controller} endpoints needed by the
 * agency onboarding orchestration (see {@code CORE_KERNEL_GATEWAY_SPEC.md} Bloc C).
 *
 * <p>Implemented by {@code KernelOnboardingGatewayAdapter} using the shared
 * {@code kernelWebClient} bean — never a Kernel Spring bean/type (see root
 * {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public interface IKernelOnboardingGatewayPort {

    /**
     * @param bearerAuthorization the acting user's raw {@code Authorization} header value
     *                            to forward (the Kernel enforces {@code sub}-based
     *                            ownership on several of these endpoints — e.g. actor
     *                            onboarding must carry the candidate's own token), or
     *                            {@code null}
     */
    Mono<KernelEnvelope> invoke(HttpMethod method, String kernelPath, JsonNode body, String bearerAuthorization);
}
