package com.yowyob.tiibntick.core.hrm.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;

/**
 * Wire-format mirror of the Kernel's own {@code ApiResponse} envelope
 * ({@code success}/{@code data}/{@code message}/{@code errorCode}/{@code timestamp}) —
 * used to deserialize responses from any of the 16 Kernel HRM controllers proxied by
 * this module (see {@code docs/kernel-api/endpoints.md}).
 *
 * <p>This is a plain JSON contract record, not a shared Kernel type — TiiBnTick talks
 * to the Kernel over HTTP only (see root {@code CLAUDE.md}). {@code data} is left as a
 * raw {@link JsonNode} because the 160 Kernel HRM operations each return a different
 * payload shape; this gateway proxies them opaquely rather than re-declaring one
 * response DTO per operation (same rationale as {@code tnt-platform-gateway-core}'s
 * {@code KernelApiEnvelope} for the auth-controller Bloc A proxy).
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
     * so every HRM proxy caller gets the same response shape across the whole Core
     * API — not the Kernel's.
     */
    public ApiResponse<JsonNode> toApiResponse() {
        if (success) {
            return ApiResponse.success(data);
        }
        String code = errorCode != null ? errorCode : "KERNEL_HRM_ERROR";
        String msg = message != null ? message : "Kernel HRM request failed";
        return ApiResponse.error(ErrorDetail.of(code, msg), null);
    }
}
