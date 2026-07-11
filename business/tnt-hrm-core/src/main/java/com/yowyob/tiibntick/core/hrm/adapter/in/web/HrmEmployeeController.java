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
 * Proxies the Kernel's {@code employee-controller} — employee membership and full lifecycle (contracts, dependents, emergency contacts, status transitions). TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Employees", description = "employee membership and full lifecycle (contracts, dependents, emergency contacts, status transitions)")
public class HrmEmployeeController extends AbstractHrmProxyController {

    public HrmEmployeeController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/employees")
    @Operation(summary = "GET /api/employees")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listEmployees(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/employees", exchange, null, authorization);
    }

    @PostMapping("/api/employees/invite")
    @Operation(summary = "POST /api/employees/invite")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createEmployeesInvite(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/employees/invite", exchange, body, authorization);
    }

    @GetMapping("/api/employees/roles")
    @Operation(summary = "GET /api/employees/roles")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listEmployeesRoles(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/employees/roles", exchange, null, authorization);
    }

    @DeleteMapping("/api/employees/{membershipId}")
    @Operation(summary = "DELETE /api/employees/{membershipId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteEmployeesByMembershipId(
            @PathVariable String membershipId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/employees/" + membershipId, exchange, null, authorization);
    }

    @PutMapping("/api/employees/{membershipId}")
    @Operation(summary = "PUT /api/employees/{membershipId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateEmployeesByMembershipId(
            @PathVariable String membershipId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/employees/" + membershipId, exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees")
    @Operation(summary = "GET /api/v1/hrm/employees")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listEmployees2(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/employees")
    @Operation(summary = "POST /api/v1/hrm/employees")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createEmployees(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/employees", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/check-cnps")
    @Operation(summary = "GET /api/v1/hrm/employees/check-cnps")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listEmployeesCheckCnps(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/check-cnps", exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/me/photo")
    @Operation(summary = "PUT /api/v1/hrm/employees/me/photo")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> photoEmployeesMe(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/me/photo", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeId(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateEmployeesByEmployeeId(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId, exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/contracts")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/contracts")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdContracts(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/contracts", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/employees/{employeeId}/contracts")
    @Operation(summary = "POST /api/v1/hrm/employees/{employeeId}/contracts")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createEmployeesByEmployeeIdContracts(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/employees/" + employeeId + "/contracts", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/contracts/{contractId}")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/contracts/{contractId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdContractsByContractId(
            @PathVariable String employeeId,
            @PathVariable String contractId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/contracts/" + contractId, exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/employees/{employeeId}/contracts/{contractId}/renew")
    @Operation(summary = "POST /api/v1/hrm/employees/{employeeId}/contracts/{contractId}/renew")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> renewEmployeesByEmployeeIdContractsByContractId(
            @PathVariable String employeeId,
            @PathVariable String contractId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/employees/" + employeeId + "/contracts/" + contractId + "/renew", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}/contracts/{contractId}/terminate")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}/contracts/{contractId}/terminate")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> terminateEmployeesByEmployeeIdContractsByContractId(
            @PathVariable String employeeId,
            @PathVariable String contractId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId + "/contracts/" + contractId + "/terminate", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/dependents")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/dependents")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdDependents(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/dependents", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/employees/{employeeId}/dependents")
    @Operation(summary = "POST /api/v1/hrm/employees/{employeeId}/dependents")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createEmployeesByEmployeeIdDependents(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/employees/" + employeeId + "/dependents", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/emergency-contacts")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/emergency-contacts")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdEmergencyContacts(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/emergency-contacts", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/employees/{employeeId}/emergency-contacts")
    @Operation(summary = "POST /api/v1/hrm/employees/{employeeId}/emergency-contacts")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createEmployeesByEmployeeIdEmergencyContacts(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/employees/" + employeeId + "/emergency-contacts", exchange, body, authorization);
    }

    @DeleteMapping("/api/v1/hrm/employees/{employeeId}/emergency-contacts/{contactId}")
    @Operation(summary = "DELETE /api/v1/hrm/employees/{employeeId}/emergency-contacts/{contactId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteEmployeesByEmployeeIdEmergencyContactsByContactId(
            @PathVariable String employeeId,
            @PathVariable String contactId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/v1/hrm/employees/" + employeeId + "/emergency-contacts/" + contactId, exchange, null, authorization);
    }

    @PatchMapping("/api/v1/hrm/employees/{employeeId}/emergency-contacts/{contactId}")
    @Operation(summary = "PATCH /api/v1/hrm/employees/{employeeId}/emergency-contacts/{contactId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> patchEmployeesByEmployeeIdEmergencyContactsByContactId(
            @PathVariable String employeeId,
            @PathVariable String contactId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PATCH, "/api/v1/hrm/employees/" + employeeId + "/emergency-contacts/" + contactId, exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/leave-balances")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/leave-balances")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdLeaveBalances(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/leave-balances", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/personal-info")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/personal-info")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdPersonalInfo(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/personal-info", exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}/personal-info")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}/personal-info")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateEmployeesByEmployeeIdPersonalInfo(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId + "/personal-info", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}/photo")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}/photo")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> photoEmployeesByEmployeeId(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId + "/photo", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/profile")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/profile")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdProfile(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/profile", exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}/reactivate")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}/reactivate")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> reactivateEmployeesByEmployeeId(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId + "/reactivate", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}/suspend")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}/suspend")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> suspendEmployeesByEmployeeId(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId + "/suspend", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/{employeeId}/terminate")
    @Operation(summary = "PUT /api/v1/hrm/employees/{employeeId}/terminate")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> terminateEmployeesByEmployeeId(
            @PathVariable String employeeId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/" + employeeId + "/terminate", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/employees/{employeeId}/timeline")
    @Operation(summary = "GET /api/v1/hrm/employees/{employeeId}/timeline")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getEmployeesByEmployeeIdTimeline(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/employees/" + employeeId + "/timeline", exchange, null, authorization);
    }
}
