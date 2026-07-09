package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Request body for {@code POST /api/v1/sso/token/exchange}. {@code agencyId} is optional.
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
