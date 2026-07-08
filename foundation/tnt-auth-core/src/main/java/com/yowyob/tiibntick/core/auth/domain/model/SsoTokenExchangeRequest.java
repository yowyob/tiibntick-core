package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Request body for {@code POST /api/v1/sso/token/exchange} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.3. {@code agencyId} is optional.
 *
 * @author MANFOUO Braun
 */
public record SsoTokenExchangeRequest(
        String sharedSessionToken,
        String contextId,
        String organizationId,
        String serviceCode,
        String agencyId
) {
}
