package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A single scope granted to a {@link PlatformClient}, in the shared {@code resource:action}
 * format (see {@code com.yowyob.tiibntick.common.security.PermissionMatcher} and
 * {@code docs/auth/platform-client-management-design.md} §2.4) — e.g. {@code "AUTH:*"},
 * {@code "SSO:*"}, {@code "DELIVERY:read"}, {@code "*"}.
 *
 * @author MANFOUO Braun
 */
public record ClientPermission(
        UUID id,
        UUID platformClientId,
        String scope,
        Instant grantedAt,
        String grantedBy
) {
}
