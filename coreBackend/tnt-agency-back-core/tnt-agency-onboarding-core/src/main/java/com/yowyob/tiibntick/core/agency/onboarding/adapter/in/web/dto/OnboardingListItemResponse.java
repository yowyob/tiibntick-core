package com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record OnboardingListItemResponse(
        UUID applicationId,
        UUID agencyId,
        UUID tenantId,
        String agencyName,
        String ownerName,
        String ownerEmail,
        String ownerPhone,
        String applicationStatus,
        String agencyStatus,
        UUID kernelBusinessActorId,
        boolean kernelIdentityReady,
        Instant submittedAt) {}
