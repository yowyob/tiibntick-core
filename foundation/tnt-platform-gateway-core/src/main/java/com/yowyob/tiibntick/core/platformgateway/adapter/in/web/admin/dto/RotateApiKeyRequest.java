package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

/**
 * Request body for {@code POST /api/v1/admin/api-keys/{keyId}/rotate}. {@code graceHours}
 * defaults to 24 when omitted (see {@code PlatformClientAdminService.rotateApiKey}).
 *
 * @author MANFOUO Braun
 */
public record RotateApiKeyRequest(
        Long graceHours,
        String reason
) {
}
