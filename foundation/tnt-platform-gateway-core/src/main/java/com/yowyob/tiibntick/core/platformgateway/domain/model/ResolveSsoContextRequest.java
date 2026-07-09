package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Request body for {@code POST /api/v1/sso/context/resolve}. {@code sharedSessionToken}
 * is optional when the caller instead supplies it via the {@code Authorization} header
 * (preferred).
 *
 * @author MANFOUO Braun
 */
public record ResolveSsoContextRequest(
        String sharedSessionToken,
        String kernelOrganizationId
) {
}
