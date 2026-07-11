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
 * Proxies the Kernel's {@code mission-order-controller} — mission orders and their acceptance/completion workflow. TiiBnTick Core performs
 * NO HRM business logic here: it only forwards the request to the Kernel and re-wraps
 * the response in the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md} and {@link AbstractHrmProxyController}).
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "HRM Mission Orders", description = "mission orders and their acceptance/completion workflow")
public class HrmMissionOrderController extends AbstractHrmProxyController {

    public HrmMissionOrderController(ProxyKernelHrmUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/hrm/mission-orders")
    @Operation(summary = "GET /api/v1/hrm/mission-orders")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listMissionOrders(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/mission-orders", exchange, null, authorization);
    }

    @PostMapping("/api/v1/hrm/mission-orders")
    @Operation(summary = "POST /api/v1/hrm/mission-orders")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createMissionOrders(
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/mission-orders", exchange, body, authorization);
    }

    @GetMapping("/api/v1/hrm/mission-orders/declined")
    @Operation(summary = "GET /api/v1/hrm/mission-orders/declined")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listMissionOrdersDeclined(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/mission-orders/declined", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/mission-orders/pending-acceptance")
    @Operation(summary = "GET /api/v1/hrm/mission-orders/pending-acceptance")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listMissionOrdersPendingAcceptance(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/mission-orders/pending-acceptance", exchange, null, authorization);
    }

    @GetMapping("/api/v1/hrm/mission-orders/{id}")
    @Operation(summary = "GET /api/v1/hrm/mission-orders/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getMissionOrdersById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/hrm/mission-orders/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/hrm/mission-orders/{id}/accept")
    @Operation(summary = "PUT /api/v1/hrm/mission-orders/{id}/accept")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> acceptMissionOrdersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/mission-orders/" + id + "/accept", exchange, body, authorization);
    }

    @PostMapping("/api/v1/hrm/mission-orders/{id}/amend")
    @Operation(summary = "POST /api/v1/hrm/mission-orders/{id}/amend")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createMissionOrdersByIdAmend(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/hrm/mission-orders/" + id + "/amend", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/mission-orders/{id}/cancel")
    @Operation(summary = "PUT /api/v1/hrm/mission-orders/{id}/cancel")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> cancelMissionOrdersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/mission-orders/" + id + "/cancel", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/mission-orders/{id}/complete")
    @Operation(summary = "PUT /api/v1/hrm/mission-orders/{id}/complete")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> completeMissionOrdersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/mission-orders/" + id + "/complete", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/mission-orders/{id}/decline")
    @Operation(summary = "PUT /api/v1/hrm/mission-orders/{id}/decline")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> declineMissionOrdersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/mission-orders/" + id + "/decline", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/mission-orders/{id}/issue")
    @Operation(summary = "PUT /api/v1/hrm/mission-orders/{id}/issue")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> issueMissionOrdersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/mission-orders/" + id + "/issue", exchange, body, authorization);
    }

    @PutMapping("/api/v1/hrm/mission-orders/{id}/start")
    @Operation(summary = "PUT /api/v1/hrm/mission-orders/{id}/start")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> startMissionOrdersById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/hrm/mission-orders/" + id + "/start", exchange, body, authorization);
    }
}
