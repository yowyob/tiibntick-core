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
 * Proxies the Kernel's {@code training-controller} — trainings, training requests and enrollments. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Training", description = "trainings, training requests and enrollments")
public class HrmTrainingController extends AbstractHrmProxyController {

    public HrmTrainingController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/trainings")
    @Operation(summary = "GET /api/v1/hrm/trainings")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listTrainings(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/trainings", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/trainings")
    @Operation(summary = "POST /api/v1/hrm/trainings")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createTrainings(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/trainings", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/trainings/enrollments/employee/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/trainings/enrollments/employee/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getTrainingsEnrollmentsEmployeeByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/trainings/enrollments/employee/" + employeeId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/enrollments/{enrollmentId}/cancel")
    @Operation(summary = "PUT /api/v1/hrm/trainings/enrollments/{enrollmentId}/cancel")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> cancelTrainingsEnrollmentsByEnrollmentId(
            @PathVariable String enrollmentId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/enrollments/" + enrollmentId + "/cancel", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/enrollments/{enrollmentId}/complete")
    @Operation(summary = "PUT /api/v1/hrm/trainings/enrollments/{enrollmentId}/complete")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> completeTrainingsEnrollmentsByEnrollmentId(
            @PathVariable String enrollmentId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/enrollments/" + enrollmentId + "/complete", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/trainings/requests")
    @Operation(summary = "GET /api/v1/hrm/trainings/requests")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listTrainingsRequests(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/trainings/requests", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/trainings/requests")
    @Operation(summary = "POST /api/v1/hrm/trainings/requests")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createTrainingsRequests(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/trainings/requests", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/requests/{requestId}/approve")
    @Operation(summary = "PUT /api/v1/hrm/trainings/requests/{requestId}/approve")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> approveTrainingsRequestsByRequestId(
            @PathVariable String requestId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/requests/" + requestId + "/approve", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/requests/{requestId}/cancel")
    @Operation(summary = "PUT /api/v1/hrm/trainings/requests/{requestId}/cancel")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> cancelTrainingsRequestsByRequestId(
            @PathVariable String requestId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/requests/" + requestId + "/cancel", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/requests/{requestId}/reject")
    @Operation(summary = "PUT /api/v1/hrm/trainings/requests/{requestId}/reject")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> rejectTrainingsRequestsByRequestId(
            @PathVariable String requestId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/requests/" + requestId + "/reject", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/trainings/{trainingId}")
    @Operation(summary = "GET /api/v1/hrm/trainings/{trainingId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getTrainingsByTrainingId(
            @PathVariable String trainingId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/trainings/" + trainingId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/{trainingId}/cancel")
    @Operation(summary = "PUT /api/v1/hrm/trainings/{trainingId}/cancel")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> cancelTrainingsByTrainingId(
            @PathVariable String trainingId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/" + trainingId + "/cancel", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/{trainingId}/complete")
    @Operation(summary = "PUT /api/v1/hrm/trainings/{trainingId}/complete")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> completeTrainingsByTrainingId(
            @PathVariable String trainingId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/" + trainingId + "/complete", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/trainings/{trainingId}/enrollments")
    @Operation(summary = "GET /api/v1/hrm/trainings/{trainingId}/enrollments")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getTrainingsByTrainingIdEnrollments(
            @PathVariable String trainingId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/trainings/" + trainingId + "/enrollments", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/trainings/{trainingId}/enrollments")
    @Operation(summary = "POST /api/v1/hrm/trainings/{trainingId}/enrollments")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createTrainingsByTrainingIdEnrollments(
            @PathVariable String trainingId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/trainings/" + trainingId + "/enrollments", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/trainings/{trainingId}/start")
    @Operation(summary = "PUT /api/v1/hrm/trainings/{trainingId}/start")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> startTrainingsByTrainingId(
            @PathVariable String trainingId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/trainings/" + trainingId + "/start", exchange, body, authorization);
    }
}
