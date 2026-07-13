package com.yowyob.tiibntick.core.agency.intake.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_intake", name = "client_intake_requests")
public class ClientIntakeRequestEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("branch_id")
    private UUID branchId;

    @Column("reference_code")
    private String referenceCode;

    private String source;
    private String status;

    @Column("sender_name")
    private String senderName;

    @Column("sender_phone")
    private String senderPhone;

    @Column("recipient_name")
    private String recipientName;

    @Column("recipient_phone")
    private String recipientPhone;

    @Column("pickup_address")
    private String pickupAddress;

    @Column("delivery_address")
    private String deliveryAddress;

    @Column("weight_kg")
    private Double weightKg;

    @Column("packages_count")
    private Integer packagesCount;

    @Column("delivery_mode")
    private String deliveryMode;

    @Column("target_hub_id")
    private UUID targetHubId;

    private String notes;

    @Column("mission_id")
    private UUID missionId;

    @Column("tracking_code")
    private String trackingCode;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("reviewed_by")
    private UUID reviewedBy;

    @Column("reviewed_at")
    private Instant reviewedAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
