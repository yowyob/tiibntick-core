package com.yowyob.tiibntick.core.auth.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;

/**
 * Wire-format mirror of the Kernel's own {@code ApiResponse} envelope
 * ({@code success}/{@code data}/{@code message}/{@code errorCode}/{@code timestamp}) —
 * used only to deserialize responses from {@code auth-controller} Kernel endpoints.
 *
 * <p>This is a plain JSON contract record, not a shared Kernel type — TiiBnTick talks
 * to the Kernel over HTTP only (see root {@code CLAUDE.md}). {@code data} is left as a
 * raw {@link JsonNode} because each Kernel auth endpoint returns a different payload
 * shape; the gateway proxies it opaquely rather than re-declaring ~25 response DTOs.
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
     * so every platform backend gets the same response shape across the whole Core
     * API — not the Kernel's.
     */
    public ApiResponse<JsonNode> toApiResponse() {
        if (success) {
            return ApiResponse.success(data);
        }
        String code = errorCode != null ? errorCode : "KERNEL_AUTH_ERROR";
        String message1 = message != null ? message : "Kernel authentication request failed";
        return ApiResponse.error(ErrorDetail.of(code, message1), null);
    }
}
