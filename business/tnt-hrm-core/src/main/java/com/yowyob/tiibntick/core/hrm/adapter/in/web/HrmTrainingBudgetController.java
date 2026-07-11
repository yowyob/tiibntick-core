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
 * Proxies the Kernel's {@code training-budget-controller} — training budgets and their engagement/realization. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Training Budgets", description = "training budgets and their engagement/realization")
public class HrmTrainingBudgetController extends AbstractHrmProxyController {

    public HrmTrainingBudgetController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/training-budgets")
    @Operation(summary = "GET /api/v1/hrm/training-budgets")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listTrainingBudgets(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/training-budgets", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/training-budgets")
    @Operation(summary = "POST /api/v1/hrm/training-budgets")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createTrainingBudgets(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/training-budgets", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/training-budgets/{id}")
    @Operation(summary = "GET /api/v1/hrm/training-budgets/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getTrainingBudgetsById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/training-budgets/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/training-budgets/{id}/engage")
    @Operation(summary = "PUT /api/v1/hrm/training-budgets/{id}/engage")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> engageTrainingBudgetsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/training-budgets/" + id + "/engage", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/training-budgets/{id}/realiser")
    @Operation(summary = "PUT /api/v1/hrm/training-budgets/{id}/realiser")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> realiserTrainingBudgetsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/training-budgets/" + id + "/realiser", exchange, body, authorization);
    }
}
