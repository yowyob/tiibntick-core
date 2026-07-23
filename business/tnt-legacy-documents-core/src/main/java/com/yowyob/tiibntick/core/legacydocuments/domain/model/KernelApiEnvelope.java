package com.yowyob.tiibntick.core.legacydocuments.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;

/**
 * Wire-format mirror of the Kernel's own {@code ApiResponse} envelope
 * ({@code success}/{@code data}/{@code message}/{@code errorCode}/{@code timestamp}) —
 * used to deserialize responses from any of the 7 document types proxied by this module
 * ({@code billing-legacy-documents-controller}, see
 * {@code docs/kernel-api/endpoints.md:4224-4702}).
 *
 * <p>This is a plain JSON contract record, not a shared Kernel type — TiiBnTick talks
 * to the Kernel over HTTP only (see root {@code CLAUDE.md}). {@code data} is left as a
 * raw {@link JsonNode} because the 64 operations each return one of several payload
 * shapes ({@code CommercialDocumentView}, {@code PaymentView}); this gateway proxies them
 * opaquely rather than re-declaring one response DTO per operation (same rationale as
 * {@code tnt-hrm-core}'s identically-named class — see the *Bulk Kernel Proxy Codegen
 * Technique* precedent this module reuses).
 *
 * @author MANFOUO Braun
 */
public record KernelApiEnvelope(
        boolean success,
        JsonNode data,
        String message,
        String errorCode,
        String timestamp
) {

    /**
     * Translates this Kernel envelope into TiiBnTick Core's own {@link ApiResponse},
     * so every commercial-document proxy caller gets the same response shape across the
     * whole Core API — not the Kernel's.
     */
    public ApiResponse<JsonNode> toApiResponse() {
        if (success) {
            return ApiResponse.success(data);
        }
        String code = errorCode != null ? errorCode : "KERNEL_LEGACY_DOCUMENT_ERROR";
        String msg = message != null ? message : "Kernel commercial-document request failed";
        return ApiResponse.error(ErrorDetail.of(code, msg), null);
    }
}
