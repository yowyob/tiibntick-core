package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.UUID;

/**
 * Response body for {@code POST .../kernel-identity} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.2.
 *
 * @author MANFOUO Braun
 */
public record AgencyKernelIdentityResponse(
        UUID kernelBusinessActorId
) {
}
