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
 * Proxies the Kernel's {@code review-controller} — performance reviews and their objectives. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Reviews", description = "performance reviews and their objectives")
public class HrmReviewController extends AbstractHrmProxyController {

    public HrmReviewController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/reviews")
    @Operation(summary = "GET /api/v1/hrm/reviews")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listReviews(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/reviews", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/reviews")
    @Operation(summary = "POST /api/v1/hrm/reviews")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createReviews(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/reviews", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/reviews/employee/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/reviews/employee/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getReviewsEmployeeByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/reviews/employee/" + employeeId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/reviews/objectives/{objectiveId}/evaluate")
    @Operation(summary = "PUT /api/v1/hrm/reviews/objectives/{objectiveId}/evaluate")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> evaluateReviewsObjectivesByObjectiveId(
            @PathVariable String objectiveId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/reviews/objectives/" + objectiveId + "/evaluate", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/reviews/{reviewId}")
    @Operation(summary = "GET /api/v1/hrm/reviews/{reviewId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getReviewsByReviewId(
            @PathVariable String reviewId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/reviews/" + reviewId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/reviews/{reviewId}/acknowledge")
    @Operation(summary = "PUT /api/v1/hrm/reviews/{reviewId}/acknowledge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> acknowledgeReviewsByReviewId(
            @PathVariable String reviewId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/reviews/" + reviewId + "/acknowledge", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/reviews/{reviewId}/finalize")
    @Operation(summary = "PUT /api/v1/hrm/reviews/{reviewId}/finalize")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> finalizeReviewsByReviewId(
            @PathVariable String reviewId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/reviews/" + reviewId + "/finalize", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/reviews/{reviewId}/objectives")
    @Operation(summary = "GET /api/v1/hrm/reviews/{reviewId}/objectives")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getReviewsByReviewIdObjectives(
            @PathVariable String reviewId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/reviews/" + reviewId + "/objectives", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/reviews/{reviewId}/objectives")
    @Operation(summary = "POST /api/v1/hrm/reviews/{reviewId}/objectives")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createReviewsByReviewIdObjectives(
            @PathVariable String reviewId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/reviews/" + reviewId + "/objectives", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/reviews/{reviewId}/submit")
    @Operation(summary = "PUT /api/v1/hrm/reviews/{reviewId}/submit")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> submitReviewsByReviewId(
            @PathVariable String reviewId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/reviews/" + reviewId + "/submit", exchange, body, authorization);
    }
}
