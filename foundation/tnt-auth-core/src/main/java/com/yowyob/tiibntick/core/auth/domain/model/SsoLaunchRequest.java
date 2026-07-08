package com.yowyob.tiibntick.core.auth.domain.model;

/**
 * Request body for the optional composite {@code POST /api/v1/sso/yowyob/launch} —
 * chains {@code context/resolve} + {@code token/exchange} into one call. See
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.4. {@code kernelAgencyId}/{@code branchName}
 * are optional.
 *
 * @author MANFOUO Braun
 */
public record SsoLaunchRequest(
        String app,
        String sharedSessionToken,
        String kernelOrganizationId,
        String kernelAgencyId,
        String branchName
) {
}
