package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Response body for {@code POST /api/v1/sso/token/exchange}.
 *
 * @author MANFOUO Braun
 */
public record SsoTokenExchangeResponse(
        String accessToken
) {
}
