package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code Parcel} aggregate.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_parcels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("weight_kg")
    private Double weightKg;

    @Column("width_cm")
    private Double widthCm;

    @Column("height_cm")
    private Double heightCm;

    @Column("length_cm")
    private Double lengthCm;

    @Column("fragile")
    private Boolean fragile;

    @Column("perishable")
    private Boolean perishable;

    @Column("description")
    private String description;

    @Column("designation")
    private String designation;

    @Column("thickness_cm")
    private Double thicknessCm;

    @Column("photo_url")
    private String photoUrl;

    // ── lot evt-l2: state, custody, tracking code (spec §1.2, D-136) ──────────────────────────
    @Column("tracking_code")
    private String trackingCode;
    @Column("status")
    private String status;
    @Column("status_changed_at")
    private Instant statusChangedAt;
    @Column("previous_status_before_dispute")
    private String previousStatusBeforeDispute;
    @Column("open_dispute_id")
    private UUID openDisputeId;
    @Column("dispute_freezes_delivery")
    private Boolean disputeFreezesDelivery;
    @Column("current_delivery_id")
    private UUID currentDeliveryId;
    /** JSON array of the delivery ids of the parcel, in creation order. */
    @Column("delivery_ids")
    private String deliveryIds;
    @Column("custody_holder_type")
    private String custodyHolderType;
    @Column("custody_holder_id")
    private UUID custodyHolderId;
    @Column("custody_hub_id")
    private UUID custodyHubId;
    @Column("custody_hub_package_entry_id")
    private UUID custodyHubPackageEntryId;
    @Column("return_cause")
    private String returnCause;
    @Column("return_cost_payer")
    private String returnCostPayer;
    @Column("closed_reason")
    private String closedReason;
    @Column("failed_return_count")
    private Integer failedReturnCount;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Long version;
}
