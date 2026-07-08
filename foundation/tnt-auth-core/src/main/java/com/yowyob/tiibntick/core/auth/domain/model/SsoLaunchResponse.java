package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Response body for the optional composite {@code POST /api/v1/sso/yowyob/launch} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.4.
 *
 * @author MANFOUO Braun
 */
public record SsoLaunchResponse(
        String redirectUrl,
        String app,
        boolean branchScoped
) {
}
