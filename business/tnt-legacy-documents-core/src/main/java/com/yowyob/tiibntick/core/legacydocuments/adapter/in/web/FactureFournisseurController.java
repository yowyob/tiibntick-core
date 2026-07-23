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
 * {@code /api/facture-fournisseurs} — supplier invoices (facture fournisseur). TiiBnTick Core performs NO commercial-document business
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
@Tag(name = "Legacy Documents — Facture Fournisseur", description = "supplier invoices (facture fournisseur)")
public class FactureFournisseurController extends AbstractLegacyDocumentProxyController {

    public FactureFournisseurController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/facture-fournisseurs")
    @Operation(summary = "GET /api/facture-fournisseurs")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listFactureFournisseur(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/facture-fournisseurs", exchange, null, authorization);
    }

    @PostMapping("/api/facture-fournisseurs")
    @Operation(summary = "POST /api/facture-fournisseurs")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createFactureFournisseur(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/facture-fournisseurs", exchange, body, authorization);
    }

    @GetMapping("/api/facture-fournisseurs/{id}")
    @Operation(summary = "GET /api/facture-fournisseurs/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getFactureFournisseurById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/facture-fournisseurs/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/facture-fournisseurs/{id}")
    @Operation(summary = "PUT /api/facture-fournisseurs/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateFactureFournisseurById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/facture-fournisseurs/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/facture-fournisseurs/{id}/payments/bank")
    @Operation(summary = "POST /api/facture-fournisseurs/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentFactureFournisseurById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/facture-fournisseurs/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/facture-fournisseurs/{id}/payments/cashier")
    @Operation(summary = "POST /api/facture-fournisseurs/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentFactureFournisseurById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/facture-fournisseurs/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/facture-fournisseurs/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/facture-fournisseurs/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceFactureFournisseurById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/facture-fournisseurs/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/facture-fournisseurs/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/facture-fournisseurs/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillFactureFournisseurById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/facture-fournisseurs/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }
}
