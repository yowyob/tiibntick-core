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
 * {@code /api/factures-proforma} — proforma invoices (facture proforma). TiiBnTick Core performs NO commercial-document business
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
@Tag(name = "Legacy Documents — Facture Proforma", description = "proforma invoices (facture proforma)")
public class FactureProformaController extends AbstractLegacyDocumentProxyController {

    public FactureProformaController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/factures-proforma")
    @Operation(summary = "GET /api/factures-proforma")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listFactureProforma(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/factures-proforma", exchange, null, authorization);
    }

    @PostMapping("/api/factures-proforma")
    @Operation(summary = "POST /api/factures-proforma")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createFactureProforma(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/factures-proforma", exchange, body, authorization);
    }

    @GetMapping("/api/factures-proforma/{id}")
    @Operation(summary = "GET /api/factures-proforma/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getFactureProformaById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/factures-proforma/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/factures-proforma/{id}")
    @Operation(summary = "PUT /api/factures-proforma/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateFactureProformaById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/factures-proforma/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/factures-proforma/{id}/payments/bank")
    @Operation(summary = "POST /api/factures-proforma/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentFactureProformaById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/factures-proforma/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/factures-proforma/{id}/payments/cashier")
    @Operation(summary = "POST /api/factures-proforma/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentFactureProformaById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/factures-proforma/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/factures-proforma/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/factures-proforma/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceFactureProformaById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/factures-proforma/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/factures-proforma/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/factures-proforma/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillFactureProformaById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/factures-proforma/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }

    @GetMapping("/api/factures-proforma/client/{idClient}")
    @Operation(summary = "GET /api/factures-proforma/client/{idClient}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listFactureProformaByClient(
            @PathVariable String idClient,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/factures-proforma/client/" + idClient, exchange, null, authorization);
    }

    @DeleteMapping("/api/factures-proforma/{id}")
    @Operation(summary = "DELETE /api/factures-proforma/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteFactureProformaById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/factures-proforma/" + id, exchange, null, authorization);
    }
}
