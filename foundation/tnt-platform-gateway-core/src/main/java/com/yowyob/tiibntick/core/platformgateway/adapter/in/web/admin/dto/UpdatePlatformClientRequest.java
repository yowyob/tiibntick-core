package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;

/**
 * Request body for {@code PATCH /api/v1/admin/platform-clients/{id}} — any field left
 * {@code null} is left unchanged (partial update semantics).
 *
 * @author MANFOUO Braun
 */
public record UpdatePlatformClientRequest(
        String name,
        String description,
        String contactEmail,
        ClientStatus status
) {
}
