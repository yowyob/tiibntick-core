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
 * Proxies the Kernel's {@code expense-controller} — expense reports and their approval/reimbursement workflow. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Expenses", description = "expense reports and their approval/reimbursement workflow")
public class HrmExpenseController extends AbstractHrmProxyController {

    public HrmExpenseController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/expenses")
    @Operation(summary = "GET /api/v1/hrm/expenses")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listExpenses(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/expenses", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/expenses")
    @Operation(summary = "POST /api/v1/hrm/expenses")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createExpenses(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/expenses", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/expenses/{id}")
    @Operation(summary = "GET /api/v1/hrm/expenses/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getExpensesById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/expenses/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/expenses/{id}/approve")
    @Operation(summary = "PUT /api/v1/hrm/expenses/{id}/approve")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> approveExpensesById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/expenses/" + id + "/approve", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/expenses/{id}/lines")
    @Operation(summary = "GET /api/v1/hrm/expenses/{id}/lines")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getExpensesByIdLines(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/expenses/" + id + "/lines", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/expenses/{id}/lines")
    @Operation(summary = "POST /api/v1/hrm/expenses/{id}/lines")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createExpensesByIdLines(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/expenses/" + id + "/lines", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/expenses/{id}/reimburse")
    @Operation(summary = "PUT /api/v1/hrm/expenses/{id}/reimburse")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateExpensesByIdReimburse(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/expenses/" + id + "/reimburse", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/expenses/{id}/reject")
    @Operation(summary = "PUT /api/v1/hrm/expenses/{id}/reject")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> rejectExpensesById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/expenses/" + id + "/reject", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/expenses/{id}/submit")
    @Operation(summary = "PUT /api/v1/hrm/expenses/{id}/submit")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> submitExpensesById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/expenses/" + id + "/submit", exchange, body, authorization);
    }
}
