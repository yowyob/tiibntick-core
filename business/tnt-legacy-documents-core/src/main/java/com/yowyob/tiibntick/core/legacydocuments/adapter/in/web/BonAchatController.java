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
 * {@code /api/bons-achat} — purchase receipts (bon d'achat). TiiBnTick Core performs NO commercial-document business
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
@Tag(name = "Legacy Documents — Bon d'Achat", description = "purchase receipts (bon d'achat)")
public class BonAchatController extends AbstractLegacyDocumentProxyController {

    public BonAchatController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/bons-achat")
    @Operation(summary = "GET /api/bons-achat")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listBonAchat(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bons-achat", exchange, null, authorization);
    }

    @PostMapping("/api/bons-achat")
    @Operation(summary = "POST /api/bons-achat")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createBonAchat(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-achat", exchange, body, authorization);
    }

    @GetMapping("/api/bons-achat/{id}")
    @Operation(summary = "GET /api/bons-achat/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getBonAchatById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bons-achat/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/bons-achat/{id}")
    @Operation(summary = "PUT /api/bons-achat/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateBonAchatById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/bons-achat/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/bons-achat/{id}/payments/bank")
    @Operation(summary = "POST /api/bons-achat/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentBonAchatById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-achat/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/bons-achat/{id}/payments/cashier")
    @Operation(summary = "POST /api/bons-achat/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentBonAchatById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-achat/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/bons-achat/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/bons-achat/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceBonAchatById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-achat/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/bons-achat/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/bons-achat/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillBonAchatById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-achat/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }

    @DeleteMapping("/api/bons-achat/{id}")
    @Operation(summary = "DELETE /api/bons-achat/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteBonAchatById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/bons-achat/" + id, exchange, null, authorization);
    }
}
