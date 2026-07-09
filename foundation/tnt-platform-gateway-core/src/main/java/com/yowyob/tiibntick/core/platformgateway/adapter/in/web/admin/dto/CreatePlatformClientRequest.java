package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;

/**
 * Request body for {@code POST /api/v1/admin/platform-clients}.
 *
 * @author MANFOUO Braun
 */
public record CreatePlatformClientRequest(
        String name,
        String platformCode,
        Environment environment,
        String description,
        String contactEmail
) {
}
