package com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record OnboardingApplicationResponse(
        UUID applicationId,
        UUID agencyId,
        UUID tenantId,
        String applicationStatus,
        UUID kernelBusinessActorId,
        boolean kernelIdentityReady,
        Instant submittedAt) {}
