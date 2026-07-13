package com.yowyob.tiibntick.core.agency.onboarding.application.mapper;

import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingApplicationResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingDetailResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingListItemResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.out.persistence.entity.OnboardingApplicationEntity;
import com.yowyob.tiibntick.core.agency.onboarding.domain.OnboardingApplication;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;

public final class OnboardingMapper {

    private OnboardingMapper() {}

    public static OnboardingApplicationEntity toEntity(OnboardingApplication app) {
        OnboardingApplicationEntity e = new OnboardingApplicationEntity();
        e.setId(app.getId());
        e.setTenantId(app.getTenantId());
        e.setAgencyId(app.getAgencyId());
        e.setApplicantUserId(app.getApplicantUserId());
        e.setLegalName(app.getLegalName());
        e.setOwnerName(app.getOwnerName());
        e.setOwnerEmail(app.getOwnerEmail());
        e.setOwnerPhone(app.getOwnerPhone());
        e.setOwnerNationalId(app.getOwnerNationalId());
        e.setOwnerIdType(app.getOwnerIdType());
        e.setDocCniKey(app.getDocCniKey());
        e.setDocRccmKey(app.getDocRccmKey());
        e.setDocProofKey(app.getDocProofKey());
        e.setApplicationStatus(app.getApplicationStatus().name());
        e.setRejectionReason(app.getRejectionReason());
        e.setReviewedBy(app.getReviewedBy());
        e.setReviewedAt(app.getReviewedAt());
        e.setKernelBusinessActorId(app.getKernelBusinessActorId());
        e.setKernelIdentityCompletedAt(app.getKernelIdentityCompletedAt());
        e.setCreatedAt(app.getCreatedAt());
        e.setUpdatedAt(app.getUpdatedAt());
        return e;
    }

    public static OnboardingApplication toDomain(OnboardingApplicationEntity e) {
        return new OnboardingApplication(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getApplicantUserId(),
                e.getLegalName(), e.getOwnerName(), e.getOwnerEmail(), e.getOwnerPhone(),
                e.getOwnerNationalId(), e.getOwnerIdType(),
                e.getDocCniKey(), e.getDocRccmKey(), e.getDocProofKey(),
                OnboardingApplication.ApplicationStatus.valueOf(e.getApplicationStatus()),
                e.getRejectionReason(), e.getReviewedBy(), e.getReviewedAt(),
                e.getKernelBusinessActorId(), e.getKernelIdentityCompletedAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static OnboardingApplicationResponse toResponse(OnboardingApplication app) {
        return new OnboardingApplicationResponse(
                app.getId(), app.getAgencyId(), app.getTenantId(),
                app.getApplicationStatus().name(),
                app.getKernelBusinessActorId(), app.isKernelIdentityReady(),
                app.getCreatedAt());
    }

    public static OnboardingListItemResponse toListItem(
            OnboardingApplication app, AgencyRegistryResponse agency) {
        return new OnboardingListItemResponse(
                app.getId(), app.getAgencyId(), app.getTenantId(),
                agency.name(), app.getOwnerName(), app.getOwnerEmail(), app.getOwnerPhone(),
                app.getApplicationStatus().name(), agency.status(),
                app.getKernelBusinessActorId(), app.isKernelIdentityReady(),
                app.getCreatedAt());
    }

    public static OnboardingDetailResponse toDetail(
            OnboardingApplication app, AgencyRegistryResponse agency) {
        return new OnboardingDetailResponse(
                toListItem(app, agency), agency,
                app.getLegalName(), app.getOwnerNationalId(), app.getOwnerIdType(),
                app.getDocCniKey(), app.getDocRccmKey(), app.getDocProofKey(),
                app.getRejectionReason());
    }
}
