package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

/**
 * Request body for {@code POST .../kernel-identity} — Phase 1 of agency onboarding:
 * creates the candidate's {@code BusinessActor} in the Kernel. See
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.2.
 *
 * <p>Maps onto the Kernel's {@code BusinessActorRequest} (verified against
 * {@code docs/kernel-api/schemas.md}): {@code role="OWNER"}, {@code type="BUSINESS"},
 * {@code isAvailable}/{@code isVerified}/{@code isActive}={@code true} are fixed by this
 * orchestration, matching the onboarding flow's own defaults — not caller-configurable.
 *
 * @author MANFOUO Braun
 */
public record AgencyKernelIdentityRequest(
        String ownerName,
        String businessId,
        boolean isIndividual
) {
}
