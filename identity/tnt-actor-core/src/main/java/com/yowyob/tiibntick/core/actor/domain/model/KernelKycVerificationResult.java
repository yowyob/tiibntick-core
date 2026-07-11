package com.yowyob.tiibntick.core.actor.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;

/**
 * Result of proxying one call to the Kernel's {@code kyc-verification-controller}
 * ({@code POST /api/kyc/verify}) — carries the real Kernel HTTP status alongside
 * the (possibly unwrapped) response body, since that endpoint's documented 200
 * response ({@code DocumentAnalysisResponse}) is not wrapped in the Kernel's usual
 * {@code {success,data,...}} envelope, unlike most other Kernel endpoints.
 *
 * @author MANFOUO Braun
 */
public record KernelKycVerificationResult(
        int httpStatus,
        boolean success,
        JsonNode data,
        String errorCode,
        String message
) {

    /**
     * Translates this result into TiiBnTick Core's own {@link ApiResponse}, so the
     * KYC verification proxy responds in the same envelope shape as the rest of the
     * Core API — not the Kernel's raw/unwrapped shape.
     */
    public ApiResponse<JsonNode> toApiResponse() {
        if (success) {
            return ApiResponse.success(data);
        }
        String code = errorCode != null ? errorCode : "KERNEL_KYC_VERIFY_ERROR";
        String msg = message != null ? message : "Kernel document verification failed";
        return ApiResponse.error(ErrorDetail.of(code, msg), null);
    }
}
