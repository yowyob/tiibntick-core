package com.yowyob.tiibntick.core.agency.onboarding.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_onboarding", name = "onboarding_applications")
public class OnboardingApplicationEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("applicant_user_id")
    private UUID applicantUserId;

    @Column("legal_name")
    private String legalName;

    @Column("owner_name")
    private String ownerName;

    @Column("owner_email")
    private String ownerEmail;

    @Column("owner_phone")
    private String ownerPhone;

    @Column("owner_national_id")
    private String ownerNationalId;

    @Column("owner_id_type")
    private String ownerIdType;

    @Column("doc_cni_key")
    private String docCniKey;

    @Column("doc_rccm_key")
    private String docRccmKey;

    @Column("doc_proof_key")
    private String docProofKey;

    @Column("application_status")
    private String applicationStatus;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("reviewed_by")
    private UUID reviewedBy;

    @Column("reviewed_at")
    private Instant reviewedAt;

    @Column("kernel_business_actor_id")
    private UUID kernelBusinessActorId;

    @Column("kernel_identity_completed_at")
    private Instant kernelIdentityCompletedAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
