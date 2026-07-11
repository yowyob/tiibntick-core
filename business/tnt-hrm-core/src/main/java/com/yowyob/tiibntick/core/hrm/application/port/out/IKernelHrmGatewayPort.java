package com.yowyob.tiibntick.core.hrm.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.hrm.domain.model.KernelHrmResult;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

/**
 * Secondary (outbound) port: calls the Kernel's HRM HTTP surface (rh-kpi-controller,
 * medical-controller, medical-self-service-controller, skill-controller,
 * social-declaration-controller, employee-controller, employee-self-service-controller,
 * expense-controller, leave-controller, loan-advance-controller, mission-order-controller,
 * recruitment-controller, review-controller, review-self-service-controller,
 * timesheet-controller, training-budget-controller, training-controller) — see
 * {@code docs/kernel-api/endpoints.md} for the full catalogue.
 *
 * <p>Implemented by {@code KernelHrmGatewayAdapter} using the shared {@code kernelWebClient}
 * bean — never a Kernel Spring bean/type (see root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public interface IKernelHrmGatewayPort {

    Mono<KernelHrmResult> invoke(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                  JsonNode body, String bearerAuthorization);
}
