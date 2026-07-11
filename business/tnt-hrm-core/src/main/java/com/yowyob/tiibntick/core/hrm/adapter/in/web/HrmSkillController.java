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
 * Proxies the Kernel's {@code skill-controller} — skill catalogue and employee-skill assignments. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Skills", description = "skill catalogue and employee-skill assignments")
public class HrmSkillController extends AbstractHrmProxyController {

    public HrmSkillController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/skills")
    @Operation(summary = "GET /api/v1/hrm/skills")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listSkills(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/skills", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/skills")
    @Operation(summary = "POST /api/v1/hrm/skills")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createSkills(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/skills", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/skills/employee-skills")
    @Operation(summary = "GET /api/v1/hrm/skills/employee-skills")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listSkillsEmployeeSkills(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/skills/employee-skills", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/skills/employee-skills")
    @Operation(summary = "POST /api/v1/hrm/skills/employee-skills")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createSkillsEmployeeSkills(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/skills/employee-skills", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/skills/employees/{employeeId}/skills")
    @Operation(summary = "GET /api/v1/hrm/skills/employees/{employeeId}/skills")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getSkillsEmployeesByEmployeeIdSkills(
            @PathVariable String employeeId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/skills/employees/" + employeeId + "/skills", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/skills/{id}")
    @Operation(summary = "GET /api/v1/hrm/skills/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getSkillsById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/skills/" + id, exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/skills/{skillId}/employee-skills")
    @Operation(summary = "GET /api/v1/hrm/skills/{skillId}/employee-skills")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getSkillsBySkillIdEmployeeSkills(
            @PathVariable String skillId,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/skills/" + skillId + "/employee-skills", exchange, null, authorization);
    }
}
