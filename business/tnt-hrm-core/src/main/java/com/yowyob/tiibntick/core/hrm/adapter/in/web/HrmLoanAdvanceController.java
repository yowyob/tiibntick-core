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
 * Proxies the Kernel's {@code loan-advance-controller} — employee loans/advances and their repayments. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Loans & Advances", description = "employee loans/advances and their repayments")
public class HrmLoanAdvanceController extends AbstractHrmProxyController {

    public HrmLoanAdvanceController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/loan-advances")
    @Operation(summary = "GET /api/v1/hrm/loan-advances")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listLoanAdvances(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/loan-advances", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/loan-advances")
    @Operation(summary = "POST /api/v1/hrm/loan-advances")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createLoanAdvances(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/loan-advances", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/loan-advances/employee/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/loan-advances/employee/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getLoanAdvancesEmployeeByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/loan-advances/employee/" + employeeId, exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/loan-advances/mine")
    @Operation(summary = "GET /api/v1/hrm/loan-advances/mine")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listLoanAdvancesMine(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/loan-advances/mine", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/loan-advances/mine")
    @Operation(summary = "POST /api/v1/hrm/loan-advances/mine")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createLoanAdvancesMine(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/loan-advances/mine", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/loan-advances/mine/{loanAdvanceId}/repayments")
    @Operation(summary = "GET /api/v1/hrm/loan-advances/mine/{loanAdvanceId}/repayments")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getLoanAdvancesMineByLoanAdvanceIdRepayments(
            @PathVariable String loanAdvanceId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/loan-advances/mine/" + loanAdvanceId + "/repayments", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/loan-advances/{loanAdvanceId}")
    @Operation(summary = "GET /api/v1/hrm/loan-advances/{loanAdvanceId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getLoanAdvancesByLoanAdvanceId(
            @PathVariable String loanAdvanceId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/loan-advances/" + loanAdvanceId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/loan-advances/{loanAdvanceId}/approve")
    @Operation(summary = "PUT /api/v1/hrm/loan-advances/{loanAdvanceId}/approve")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> approveLoanAdvancesByLoanAdvanceId(
            @PathVariable String loanAdvanceId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/loan-advances/" + loanAdvanceId + "/approve", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/loan-advances/{loanAdvanceId}/reject")
    @Operation(summary = "PUT /api/v1/hrm/loan-advances/{loanAdvanceId}/reject")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> rejectLoanAdvancesByLoanAdvanceId(
            @PathVariable String loanAdvanceId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/loan-advances/" + loanAdvanceId + "/reject", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/loan-advances/{loanAdvanceId}/repayments")
    @Operation(summary = "GET /api/v1/hrm/loan-advances/{loanAdvanceId}/repayments")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getLoanAdvancesByLoanAdvanceIdRepayments(
            @PathVariable String loanAdvanceId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/loan-advances/" + loanAdvanceId + "/repayments", exchange, null, authorization);
    }
}
