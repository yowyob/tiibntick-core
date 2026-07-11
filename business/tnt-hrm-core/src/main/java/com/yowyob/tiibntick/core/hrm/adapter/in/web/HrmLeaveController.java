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
 * Proxies the Kernel's {@code leave-controller} — leave requests, accrual and their approval workflow. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Leaves", description = "leave requests, accrual and their approval workflow")
public class HrmLeaveController extends AbstractHrmProxyController {

    public HrmLeaveController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/leaves")
    @Operation(summary = "GET /api/v1/hrm/leaves")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listLeaves(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/leaves", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/leaves")
    @Operation(summary = "POST /api/v1/hrm/leaves")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createLeaves(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/leaves", exchange, body, authorization);
    }

    @PostMapping("/api/v1/hrm/leaves/accrual/run")
    @Operation(summary = "POST /api/v1/hrm/leaves/accrual/run")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> runLeavesAccrual(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/leaves/accrual/run", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/leaves/employee/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/leaves/employee/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getLeavesEmployeeByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/leaves/employee/" + employeeId, exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/leaves/pending")
    @Operation(summary = "GET /api/v1/hrm/leaves/pending")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listLeavesPending(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/leaves/pending", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/leaves/{leaveRequestId}")
    @Operation(summary = "GET /api/v1/hrm/leaves/{leaveRequestId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getLeavesByLeaveRequestId(
            @PathVariable String leaveRequestId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/leaves/" + leaveRequestId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/leaves/{leaveRequestId}/approve")
    @Operation(summary = "PUT /api/v1/hrm/leaves/{leaveRequestId}/approve")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> approveLeavesByLeaveRequestId(
            @PathVariable String leaveRequestId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/leaves/" + leaveRequestId + "/approve", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/leaves/{leaveRequestId}/cancel")
    @Operation(summary = "PUT /api/v1/hrm/leaves/{leaveRequestId}/cancel")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> cancelLeavesByLeaveRequestId(
            @PathVariable String leaveRequestId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/leaves/" + leaveRequestId + "/cancel", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/leaves/{leaveRequestId}/reject")
    @Operation(summary = "PUT /api/v1/hrm/leaves/{leaveRequestId}/reject")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> rejectLeavesByLeaveRequestId(
            @PathVariable String leaveRequestId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/leaves/" + leaveRequestId + "/reject", exchange, body, authorization);
    }
}
