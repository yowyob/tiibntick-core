package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Response body for {@code POST /api/v1/sso/context/resolve} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.2.
 *
 * @author MANFOUO Braun
 */
public record ResolveSsoContextResponse(
        String contextId,
        String organizationId
) {
}
