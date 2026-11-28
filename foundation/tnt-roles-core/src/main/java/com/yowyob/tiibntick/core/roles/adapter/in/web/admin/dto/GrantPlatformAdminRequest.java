package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import java.util.UUID;

/** Body of {@code POST /api/v1/admin/platform-admins}: the Kernel user id of the future administrator. */
public record GrantPlatformAdminRequest(UUID targetUserId) {
}
