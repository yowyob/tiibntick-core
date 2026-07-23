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
 * {@code /api/bons-livraison} — delivery notes (bon de livraison). TiiBnTick Core performs NO commercial-document business
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
@Tag(name = "Legacy Documents — Bon de Livraison", description = "delivery notes (bon de livraison)")
public class BonLivraisonController extends AbstractLegacyDocumentProxyController {

    public BonLivraisonController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/bons-livraison")
    @Operation(summary = "GET /api/bons-livraison")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listBonLivraison(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bons-livraison", exchange, null, authorization);
    }

    @PostMapping("/api/bons-livraison")
    @Operation(summary = "POST /api/bons-livraison")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createBonLivraison(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-livraison", exchange, body, authorization);
    }

    @GetMapping("/api/bons-livraison/{id}")
    @Operation(summary = "GET /api/bons-livraison/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getBonLivraisonById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bons-livraison/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/bons-livraison/{id}")
    @Operation(summary = "PUT /api/bons-livraison/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateBonLivraisonById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/bons-livraison/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/bons-livraison/{id}/payments/bank")
    @Operation(summary = "POST /api/bons-livraison/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentBonLivraisonById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-livraison/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/bons-livraison/{id}/payments/cashier")
    @Operation(summary = "POST /api/bons-livraison/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentBonLivraisonById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-livraison/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/bons-livraison/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/bons-livraison/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceBonLivraisonById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-livraison/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/bons-livraison/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/bons-livraison/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillBonLivraisonById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-livraison/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }

    @GetMapping("/api/bons-livraison/client/{idClient}")
    @Operation(summary = "GET /api/bons-livraison/client/{idClient}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listBonLivraisonByClient(
            @PathVariable String idClient,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bons-livraison/client/" + idClient, exchange, null, authorization);
    }

    @DeleteMapping("/api/bons-livraison/{id}")
    @Operation(summary = "DELETE /api/bons-livraison/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteBonLivraisonById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/bons-livraison/" + id, exchange, null, authorization);
    }

    @PostMapping("/api/bons-livraison/{id}/effectuer")
    @Operation(summary = "POST /api/bons-livraison/{id}/effectuer")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> effectuerBonLivraisonById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bons-livraison/" + id + "/effectuer", exchange, null, authorization);
    }
}
