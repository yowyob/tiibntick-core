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
 * {@code /api/v1/facturation/note-credits} — credit notes (note de crédit). TiiBnTick Core performs NO commercial-document business
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
@Tag(name = "Legacy Documents — Note de Crédit", description = "credit notes (note de crédit)")
public class NoteCreditController extends AbstractLegacyDocumentProxyController {

    public NoteCreditController(ProxyKernelLegacyDocumentUseCase useCase) {
        super(useCase);
    }

    @GetMapping("/api/v1/facturation/note-credits")
    @Operation(summary = "GET /api/v1/facturation/note-credits")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> listNoteCredit(
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/facturation/note-credits", exchange, null, authorization);
    }

    @PostMapping("/api/v1/facturation/note-credits")
    @Operation(summary = "POST /api/v1/facturation/note-credits")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createNoteCredit(
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/note-credits", exchange, body, authorization);
    }

    @GetMapping("/api/v1/facturation/note-credits/{id}")
    @Operation(summary = "GET /api/v1/facturation/note-credits/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getNoteCreditById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.GET, "/api/v1/facturation/note-credits/" + id, exchange, null, authorization);
    }

    @PutMapping("/api/v1/facturation/note-credits/{id}")
    @Operation(summary = "PUT /api/v1/facturation/note-credits/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> updateNoteCreditById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.PUT, "/api/v1/facturation/note-credits/" + id, exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/note-credits/{id}/payments/bank")
    @Operation(summary = "POST /api/v1/facturation/note-credits/{id}/payments/bank")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordBankPaymentNoteCreditById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/note-credits/" + id + "/payments/bank", exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/note-credits/{id}/payments/cashier")
    @Operation(summary = "POST /api/v1/facturation/note-credits/{id}/payments/cashier")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> recordCashierPaymentNoteCreditById(
            @PathVariable String id,
            @RequestBody(required = true) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/note-credits/" + id + "/payments/cashier", exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/note-credits/{id}/sync/accounting-invoice")
    @Operation(summary = "POST /api/v1/facturation/note-credits/{id}/sync/accounting-invoice")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncAccountingInvoiceNoteCreditById(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/note-credits/" + id + "/sync/accounting-invoice", exchange, body, authorization);
    }

    @PostMapping("/api/v1/facturation/note-credits/{id}/sync/cashier-bill")
    @Operation(summary = "POST /api/v1/facturation/note-credits/{id}/sync/cashier-bill")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> syncCashierBillNoteCreditById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.POST, "/api/v1/facturation/note-credits/" + id + "/sync/cashier-bill", exchange, null, authorization);
    }

    @DeleteMapping("/api/v1/facturation/note-credits/{id}")
    @Operation(summary = "DELETE /api/v1/facturation/note-credits/{id}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> deleteNoteCreditById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy(HttpMethod.DELETE, "/api/v1/facturation/note-credits/" + id, exchange, null, authorization);
    }
}
