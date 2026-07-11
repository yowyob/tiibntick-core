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
 * Proxies the Kernel's {@code employee-self-service-controller} — self-service personal-info and emergency-contact management for the authenticated employee. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Employee Self-Service", description = "self-service personal-info and emergency-contact management for the authenticated employee")
public class HrmEmployeeSelfServiceController extends AbstractHrmProxyController {

    public HrmEmployeeSelfServiceController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @PostMapping("/api/v1/hrm/employees/me/emergency-contacts")
    @Operation(summary = "POST /api/v1/hrm/employees/me/emergency-contacts")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createEmployeesMeEmergencyContacts(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/employees/me/emergency-contacts", exchange, body, authorization);
    }

    @DeleteMapping("/api/v1/hrm/employees/me/emergency-contacts/{contactId}")
    @Operation(summary = "DELETE /api/v1/hrm/employees/me/emergency-contacts/{contactId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteEmployeesMeEmergencyContactsByContactId(
            @PathVariable String contactId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/v1/hrm/employees/me/emergency-contacts/" + contactId, exchange, null, authorization);
    }

    @PatchMapping("/api/v1/hrm/employees/me/emergency-contacts/{contactId}")
    @Operation(summary = "PATCH /api/v1/hrm/employees/me/emergency-contacts/{contactId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> patchEmployeesMeEmergencyContactsByContactId(
            @PathVariable String contactId,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PATCH, "/api/v1/hrm/employees/me/emergency-contacts/" + contactId, exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/employees/me/personal-info")
    @Operation(summary = "PUT /api/v1/hrm/employees/me/personal-info")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateEmployeesMePersonalInfo(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/employees/me/personal-info", exchange, body, authorization);
    }
}
