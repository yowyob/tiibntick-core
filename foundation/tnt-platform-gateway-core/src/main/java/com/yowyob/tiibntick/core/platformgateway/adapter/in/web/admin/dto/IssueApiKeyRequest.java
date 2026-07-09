package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import java.time.Instant;

/**
 * Request body for {@code POST /api/v1/admin/platform-clients/{id}/api-keys}.
 *
 * @author MANFOUO Braun
 */
public record IssueApiKeyRequest(
        Instant expiresAt
) {
}
