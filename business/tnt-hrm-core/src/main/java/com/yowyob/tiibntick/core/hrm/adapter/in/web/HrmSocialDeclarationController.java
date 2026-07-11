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
 * Proxies the Kernel's {@code social-declaration-controller} — social declarations (generation, submission, acknowledgement). TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Social Declarations", description = "social declarations (generation, submission, acknowledgement)")
public class HrmSocialDeclarationController extends AbstractHrmProxyController {

    public HrmSocialDeclarationController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/declarations")
    @Operation(summary = "GET /api/v1/hrm/declarations")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listDeclarations(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/declarations", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/declarations")
    @Operation(summary = "POST /api/v1/hrm/declarations")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createDeclarations(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/declarations", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/declarations/{id}")
    @Operation(summary = "GET /api/v1/hrm/declarations/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getDeclarationsById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/declarations/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/declarations/{id}/acknowledge")
    @Operation(summary = "PUT /api/v1/hrm/declarations/{id}/acknowledge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> acknowledgeDeclarationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/declarations/" + id + "/acknowledge", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/declarations/{id}/generate")
    @Operation(summary = "PUT /api/v1/hrm/declarations/{id}/generate")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> generateDeclarationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/declarations/" + id + "/generate", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/declarations/{id}/submit")
    @Operation(summary = "PUT /api/v1/hrm/declarations/{id}/submit")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> submitDeclarationsById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/declarations/" + id + "/submit", exchange, body, authorization);
    }
}
