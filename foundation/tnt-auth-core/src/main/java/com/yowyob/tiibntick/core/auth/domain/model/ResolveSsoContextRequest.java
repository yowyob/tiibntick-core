package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Request body for {@code POST /api/v1/sso/context/resolve} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.2. {@code sharedSessionToken} is optional when the
 * caller instead supplies it via the {@code Authorization} header (preferred).
 *
 * @author MANFOUO Braun
 */
public record ResolveSsoContextRequest(
        String sharedSessionToken,
        String kernelOrganizationId
) {
}
