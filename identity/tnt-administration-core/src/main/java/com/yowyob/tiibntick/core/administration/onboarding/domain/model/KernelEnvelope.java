package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wire-format mirror of the Kernel's own {@code ApiResponse} envelope
 * ({@code success}/{@code data}/{@code message}/{@code errorCode}/{@code timestamp}) —
 * used to deserialize responses from the Kernel's {@code organization-controller} /
 * {@code administration-controller} / {@code actor-controller} endpoints this
 * orchestration calls (see {@code docs/kernel-api/endpoints.md}).
 *
 * <p>Same shape as {@code tnt-auth-core}'s {@code KernelApiEnvelope} — duplicated rather
 * than shared across modules to keep each module's Kernel adapter self-contained, matching
 * the existing per-module adapter convention (see {@code KernelOrganizationAdapter} in
 * tnt-organization-core, {@code KernelRoleProvisioningAdapter} in tnt-roles-core).
 *
 * @author MANFOUO Braun
 */
public record KernelEnvelope(
        boolean success,
        JsonNode data,
        String message,
        String errorCode,
        String timestamp
) {

    public boolean failed() {
        return !success;
    }
}
