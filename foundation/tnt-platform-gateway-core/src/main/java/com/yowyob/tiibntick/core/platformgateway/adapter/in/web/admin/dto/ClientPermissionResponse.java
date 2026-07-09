package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission;

import java.time.Instant;

/**
 * Response body for a single granted scope.
 *
 * @author MANFOUO Braun
 */
public record ClientPermissionResponse(
        String scope,
        Instant grantedAt,
        String grantedBy
) {
    public static ClientPermissionResponse from(ClientPermission permission) {
        return new ClientPermissionResponse(permission.scope(), permission.grantedAt(), permission.grantedBy());
    }
}
