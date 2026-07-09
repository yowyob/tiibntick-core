package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

/**
 * Request body for {@code POST /api/v1/admin/api-keys/{keyId}/revoke}.
 *
 * @author MANFOUO Braun
 */
public record RevokeApiKeyRequest(
        String reason
) {
}
