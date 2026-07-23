package com.yowyob.tiibntick.core.legacydocuments.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.legacydocuments.application.port.in.ProxyKernelLegacyDocumentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Proxies the Kernel's {@code billing-legacy-documents-controller} operations for
 * {@code /api/v1/facturation/bon-receptions} — goods-receipt notes (bon de réception). TiiBnTick Core performs NO commercial-document business
 * logic here: it only forwards the request to the Kernel and re-wraps the response in
 * the Core's standard {@link ApiResponse} envelope (see
 * {@code docs/kernel-api/endpoints.md:4224-4702} and
 * {@link AbstractLegacyDocumentProxyController}).
 *
 * <p>Coexistence ADR: this is a generic commercial-document proxy, entirely separate from
 * {@code tnt-billing-invoice}'s own mission-billing domain — see the module {@code pom.xml}
 * description and the workstream ADR.
 *
 * @author MANFOUO Braun
 */
@RestController
@Tag(name = "Legacy Documents — Bon de Réception", description = "goods-receipt notes (bon de réception)")
public class BonReceptionController extends AbstractLegacyDocumentProxyController {

    public BonReceptionController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/facturation/bon-receptions")
    @Operation(summary = "GET /api/v1/facturation/bon-receptions")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listBonReception(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/facturation/bon-receptions", exchange, null, authorization);
    }

    @PostMapping("/api/v1/facturation/bon-receptions")
    @Operation(summary = "POST /api/v1/facturation/bon-receptions")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createBonReception(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/bon-receptions", exchange, body, authorization);
    }

    @GetMapping("/api/v1/facturation/bon-receptions/{id}")
    @Operation(summary = "GET /api/v1/facturation/bon-receptions/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getBonReceptionById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/facturation/bon-receptions/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/facturation/bon-receptions/{id}")
    @Operation(summary = "PUT /api/v1/facturation/bon-receptions/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateBonReceptionById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/facturation/bon-receptions/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/bon-receptions/{id}/payments/bank")
    @Operation(summary = "POST /api/v1/facturation/bon-receptions/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentBonReceptionById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/bon-receptions/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/bon-receptions/{id}/payments/cashier")
    @Operation(summary = "POST /api/v1/facturation/bon-receptions/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentBonReceptionById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/bon-receptions/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/bon-receptions/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/v1/facturation/bon-receptions/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceBonReceptionById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/bon-receptions/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/bon-receptions/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/v1/facturation/bon-receptions/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillBonReceptionById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/bon-receptions/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }

    @DeleteMapping("/api/v1/facturation/bon-receptions/{id}")
    @Operation(summary = "DELETE /api/v1/facturation/bon-receptions/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteBonReceptionById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/v1/facturation/bon-receptions/" + id, exchange, null, authorization);
    }
}
