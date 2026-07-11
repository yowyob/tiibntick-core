package com.yowyob.tiibntick.core.hrm.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.hrm.application.port.in.ProxyKernelHrmUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Proxies the Kernel's {@code medical-controller} — medical certificates and visits. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Medical", description = "medical certificates and visits")
public class HrmMedicalController extends AbstractHrmProxyController {

    public HrmMedicalController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/medical/certificates")
    @Operation(summary = "GET /api/v1/hrm/medical/certificates")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listMedicalCertificates(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/medical/certificates", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/medical/certificates")
    @Operation(summary = "POST /api/v1/hrm/medical/certificates")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createMedicalCertificates(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/medical/certificates", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/medical/certificates/{id}")
    @Operation(summary = "GET /api/v1/hrm/medical/certificates/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getMedicalCertificatesById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/medical/certificates/" + id, exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/medical/employees/{employeeId}/certificates")
    @Operation(summary = "GET /api/v1/hrm/medical/employees/{employeeId}/certificates")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getMedicalEmployeesByEmployeeIdCertificates(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/medical/employees/" + employeeId + "/certificates", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/medical/employees/{employeeId}/visits")
    @Operation(summary = "GET /api/v1/hrm/medical/employees/{employeeId}/visits")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getMedicalEmployeesByEmployeeIdVisits(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/medical/employees/" + employeeId + "/visits", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/medical/visits")
    @Operation(summary = "GET /api/v1/hrm/medical/visits")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listMedicalVisits(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/medical/visits", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/medical/visits")
    @Operation(summary = "POST /api/v1/hrm/medical/visits")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createMedicalVisits(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/medical/visits", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/medical/visits/{id}")
    @Operation(summary = "GET /api/v1/hrm/medical/visits/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getMedicalVisitsById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/medical/visits/" + id, exchange, null, authorization);
    }
}
