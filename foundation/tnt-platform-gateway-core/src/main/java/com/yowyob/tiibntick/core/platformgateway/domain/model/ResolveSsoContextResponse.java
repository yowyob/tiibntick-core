package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Response body for {@code POST /api/v1/sso/context/resolve}.
 *
 * @author MANFOUO Braun
 */
public record ResolveSsoContextResponse(
        String contextId,
        String organizationId
) {
}
