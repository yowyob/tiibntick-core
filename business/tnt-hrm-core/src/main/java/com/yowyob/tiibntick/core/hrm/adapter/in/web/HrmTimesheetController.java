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
 * Proxies the Kernel's {@code timesheet-controller} — timesheets and their validation workflow. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Timesheets", description = "timesheets and their validation workflow")
public class HrmTimesheetController extends AbstractHrmProxyController {

    public HrmTimesheetController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/timesheets")
    @Operation(summary = "GET /api/v1/hrm/timesheets")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listTimesheets(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/timesheets", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/timesheets")
    @Operation(summary = "POST /api/v1/hrm/timesheets")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createTimesheets(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/timesheets", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/timesheets/employee/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/timesheets/employee/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getTimesheetsEmployeeByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/timesheets/employee/" + employeeId, exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/timesheets/{timesheetId}")
    @Operation(summary = "GET /api/v1/hrm/timesheets/{timesheetId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getTimesheetsByTimesheetId(
            @PathVariable String timesheetId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/timesheets/" + timesheetId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/timesheets/{timesheetId}/reject")
    @Operation(summary = "PUT /api/v1/hrm/timesheets/{timesheetId}/reject")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> rejectTimesheetsByTimesheetId(
            @PathVariable String timesheetId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/timesheets/" + timesheetId + "/reject", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/timesheets/{timesheetId}/submit")
    @Operation(summary = "PUT /api/v1/hrm/timesheets/{timesheetId}/submit")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> submitTimesheetsByTimesheetId(
            @PathVariable String timesheetId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/timesheets/" + timesheetId + "/submit", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/timesheets/{timesheetId}/validate")
    @Operation(summary = "PUT /api/v1/hrm/timesheets/{timesheetId}/validate")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateTimesheetsByTimesheetIdValidate(
            @PathVariable String timesheetId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/timesheets/" + timesheetId + "/validate", exchange, body, authorization);
    }
}
