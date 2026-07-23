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
 * {@code /api/bon-commande} — purchase orders (bon de commande). TiiBnTick Core performs NO commercial-document business
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
@Tag(name = "Legacy Documents — Bon de Commande", description = "purchase orders (bon de commande)")
public class BonCommandeController extends AbstractLegacyDocumentProxyController {

    public BonCommandeController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/bon-commande")
    @Operation(summary = "GET /api/bon-commande")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listBonCommande(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bon-commande", exchange, null, authorization);
    }

    @PostMapping("/api/bon-commande")
    @Operation(summary = "POST /api/bon-commande")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createBonCommande(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bon-commande", exchange, body, authorization);
    }

    @GetMapping("/api/bon-commande/{id}")
    @Operation(summary = "GET /api/bon-commande/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getBonCommandeById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/bon-commande/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/bon-commande/{id}")
    @Operation(summary = "PUT /api/bon-commande/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateBonCommandeById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/bon-commande/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/bon-commande/{id}/payments/bank")
    @Operation(summary = "POST /api/bon-commande/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentBonCommandeById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bon-commande/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/bon-commande/{id}/payments/cashier")
    @Operation(summary = "POST /api/bon-commande/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentBonCommandeById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bon-commande/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/bon-commande/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/bon-commande/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceBonCommandeById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bon-commande/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/bon-commande/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/bon-commande/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillBonCommandeById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/bon-commande/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }
}
