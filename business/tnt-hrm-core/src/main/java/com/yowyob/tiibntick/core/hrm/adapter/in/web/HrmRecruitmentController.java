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
 * Proxies the Kernel's {@code recruitment-controller} — job applications, interviews, job offers and onboarding tasks. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Recruitment", description = "job applications, interviews, job offers and onboarding tasks")
public class HrmRecruitmentController extends AbstractHrmProxyController {

    public HrmRecruitmentController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @PostMapping("/api/v1/hrm/applications")
    @Operation(summary = "POST /api/v1/hrm/applications")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createApplications(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/applications", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/applications/{applicationId}/interviews")
    @Operation(summary = "GET /api/v1/hrm/applications/{applicationId}/interviews")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getApplicationsByApplicationIdInterviews(
            @PathVariable String applicationId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/applications/" + applicationId + "/interviews", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/applications/{id}")
    @Operation(summary = "GET /api/v1/hrm/applications/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getApplicationsById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/applications/" + id, exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/applications/{id}/convert-to-employee")
    @Operation(summary = "POST /api/v1/hrm/applications/{id}/convert-to-employee")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createApplicationsByIdConvertToEmployee(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/applications/" + id + "/convert-to-employee", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/applications/{id}/hire")
    @Operation(summary = "PUT /api/v1/hrm/applications/{id}/hire")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> hireApplicationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/applications/" + id + "/hire", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/applications/{id}/interview")
    @Operation(summary = "PUT /api/v1/hrm/applications/{id}/interview")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> interviewApplicationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/applications/" + id + "/interview", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/applications/{id}/offer")
    @Operation(summary = "PUT /api/v1/hrm/applications/{id}/offer")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> offerApplicationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/applications/" + id + "/offer", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/applications/{id}/reject")
    @Operation(summary = "PUT /api/v1/hrm/applications/{id}/reject")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> rejectApplicationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/applications/" + id + "/reject", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/applications/{id}/shortlist")
    @Operation(summary = "PUT /api/v1/hrm/applications/{id}/shortlist")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> shortlistApplicationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/applications/" + id + "/shortlist", exchange, body, authorization);
    }

    @PostMapping("/api/v1/hrm/interviews")
    @Operation(summary = "POST /api/v1/hrm/interviews")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createInterviews(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/interviews", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/interviews/{id}/complete")
    @Operation(summary = "PUT /api/v1/hrm/interviews/{id}/complete")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> completeInterviewsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/interviews/" + id + "/complete", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/job-offers")
    @Operation(summary = "GET /api/v1/hrm/job-offers")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listJobOffers(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/job-offers", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/job-offers")
    @Operation(summary = "POST /api/v1/hrm/job-offers")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createJobOffers(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/job-offers", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/job-offers/{id}")
    @Operation(summary = "GET /api/v1/hrm/job-offers/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getJobOffersById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/job-offers/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/job-offers/{id}/close")
    @Operation(summary = "PUT /api/v1/hrm/job-offers/{id}/close")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> closeJobOffersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/job-offers/" + id + "/close", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/job-offers/{id}/publish")
    @Operation(summary = "PUT /api/v1/hrm/job-offers/{id}/publish")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> publishJobOffersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/job-offers/" + id + "/publish", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/job-offers/{jobOfferId}/applications")
    @Operation(summary = "GET /api/v1/hrm/job-offers/{jobOfferId}/applications")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getJobOffersByJobOfferIdApplications(
            @PathVariable String jobOfferId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/job-offers/" + jobOfferId + "/applications", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/onboarding-tasks")
    @Operation(summary = "POST /api/v1/hrm/onboarding-tasks")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createOnboardingTasks(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/onboarding-tasks", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/onboarding-tasks/employee/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/onboarding-tasks/employee/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getOnboardingTasksEmployeeByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/onboarding-tasks/employee/" + employeeId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/onboarding-tasks/{id}/complete")
    @Operation(summary = "PUT /api/v1/hrm/onboarding-tasks/{id}/complete")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> completeOnboardingTasksById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/onboarding-tasks/" + id + "/complete", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/onboarding-tasks/{id}/start")
    @Operation(summary = "PUT /api/v1/hrm/onboarding-tasks/{id}/start")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> startOnboardingTasksById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/onboarding-tasks/" + id + "/start", exchange, body, authorization);
    }
}
