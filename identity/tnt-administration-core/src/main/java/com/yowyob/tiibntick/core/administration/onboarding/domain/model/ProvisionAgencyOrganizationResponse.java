package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.UUID;

/**
 * Response body for {@code POST .../provision} — see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.3.
 *
 * @author MANFOUO Braun
 */
public record ProvisionAgencyOrganizationResponse(
        UUID kernelOrganizationId,
        UUID kernelBusinessActorId
) {
}
