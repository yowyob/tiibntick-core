package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

/** Body of {@code POST /api/v1/admin/platform-admins/revocations/foreign-tenant} (decision D-165). */
public record ForeignTenantRevocationRequest(String confirmationToken, String reason) {
}
