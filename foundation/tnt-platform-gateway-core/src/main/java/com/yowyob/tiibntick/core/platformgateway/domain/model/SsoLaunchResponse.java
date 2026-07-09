package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Response body for the optional composite {@code POST /api/v1/sso/yowyob/launch}.
 *
 * @author MANFOUO Braun
 */
public record SsoLaunchResponse(
        String redirectUrl,
        String app,
        boolean branchScoped
) {
}
