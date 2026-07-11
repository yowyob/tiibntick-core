package com.yowyob.tiibntick.core.hrm.application.port.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.hrm.domain.model.KernelHrmResult;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) use-case: lets TiiBnTick Core callers manage HRM (Human Resources
 * Management) data — employees, leaves, expenses, loans/advances, medical records,
 * mission orders, recruitment, reviews, skills, social declarations, timesheets,
 * training — by talking to TiiBnTick Core only, never to the Kernel directly.
 *
 * <p>Implemented by {@code KernelHrmGatewayService}, exposed by the 16 {@code Hrm*Controller}
 * classes under {@code adapter/in/web} (one per Kernel HRM controller).
 *
 * @author MANFOUO Braun
 */
public interface ProxyKernelHrmUseCase {

    /**
     * Executes one Kernel HRM operation. Returns the real Kernel HTTP status alongside
     * the envelope so the controller can respond with it instead of flattening every
     * outcome to 200.
     *
     * @param method               HTTP method of the Kernel endpoint
     * @param kernelPath           Kernel-relative path with path variables already
     *                             substituted, e.g. {@code /api/v1/hrm/leaves/123/approve}
     * @param queryParams          the caller's query parameters, forwarded unchanged
     * @param body                 request body to forward as-is, or {@code null} when the
     *                             Kernel endpoint takes none
     * @param bearerAuthorization  the caller's raw {@code Authorization} header value, or
     *                             {@code null} when none was sent
     */
    Mono<KernelHrmResult> call(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                JsonNode body, String bearerAuthorization);
}
