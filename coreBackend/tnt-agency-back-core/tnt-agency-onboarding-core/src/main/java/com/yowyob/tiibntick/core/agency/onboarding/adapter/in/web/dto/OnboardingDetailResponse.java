package com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto;

import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;

public record OnboardingDetailResponse(
        OnboardingListItemResponse summary,
        AgencyRegistryResponse agency,
        String legalName,
        String ownerNationalId,
        String ownerIdType,
        String docCniKey,
        String docRccmKey,
        String docProofKey,
        String rejectionReason) {}
