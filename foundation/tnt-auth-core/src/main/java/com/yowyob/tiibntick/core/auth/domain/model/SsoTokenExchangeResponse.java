package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Response body for {@code POST /api/v1/sso/token/exchange} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.3.
 *
 * @author MANFOUO Braun
 */
public record SsoTokenExchangeResponse(
        String accessToken
) {
}
