package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import java.util.Set;

/**
 * Request body for {@code PUT /api/v1/admin/platform-clients/{id}/permissions} —
 * replaces the client's entire granted scope set with {@code resource:action} strings
 * (e.g. {@code "AUTH:*"}, {@code "*"}). Validated against {@code PlatformScopeRegistry}.
 *
 * @author MANFOUO Braun
 */
public record ReplaceScopesRequest(
        Set<String> scopes
) {
}
