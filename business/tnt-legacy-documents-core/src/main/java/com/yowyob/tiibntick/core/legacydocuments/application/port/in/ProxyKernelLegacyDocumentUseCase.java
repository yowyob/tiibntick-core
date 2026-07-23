package com.yowyob.tiibntick.core.legacydocuments.application.port.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.legacydocuments.domain.model.KernelDocumentResult;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) use-case: lets TiiBnTick Core callers manage generic commercial
 * documents — purchase/delivery/receipt orders, supplier invoices, proforma invoices,
 * credit notes — by talking to TiiBnTick Core only, never to the Kernel directly.
 *
 * <p>Implemented by {@code KernelLegacyDocumentGatewayService}, exposed by the 7
 * {@code *Controller} classes under {@code adapter/in/web} (one per document type).
 *
 * <p><strong>Coexistence with {@code tnt-billing-invoice}:</strong> this use-case is only
 * for generic commercial documents the Kernel's legacy ERP surface already owns — it is
 * not, and must never become, a replacement for {@code tnt-billing-invoice}'s own mission
 * billing (see the ADR in
 * {@code docs/audits/remediation/workstream-payment-billing-kernel-delegation.md}).
 *
 * @author MANFOUO Braun
 */
public interface ProxyKernelLegacyDocumentUseCase {

    /**
     * Executes one Kernel commercial-document operation. Returns the real Kernel HTTP
     * status alongside the envelope so the controller can respond with it instead of
     * flattening every outcome to 200.
     *
     * @param method               HTTP method of the Kernel endpoint
     * @param kernelPath           Kernel-relative path with path variables already
     *                             substituted, e.g. {@code /api/bons-achat/123/payments/bank}
     * @param queryParams          the caller's query parameters, forwarded unchanged
     * @param body                 request body to forward as-is, or {@code null} when the
     *                             Kernel endpoint takes none
     * @param bearerAuthorization  the caller's raw {@code Authorization} header value, or
     *                             {@code null} when none was sent
     */
    Mono<KernelDocumentResult> call(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                     JsonNode body, String bearerAuthorization);
}
