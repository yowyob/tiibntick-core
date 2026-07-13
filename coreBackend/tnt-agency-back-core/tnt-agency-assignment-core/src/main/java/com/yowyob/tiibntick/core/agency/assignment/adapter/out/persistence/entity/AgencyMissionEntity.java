package com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_assignment", name = "agency_missions")
public class AgencyMissionEntity {

    @Id @Column("id") private UUID id;
    @Column("tenant_id") private UUID tenantId;
    @Column("agency_id") private UUID agencyId;
    @Column("core_mission_id") private UUID coreMissionId;
    @Column("assigned_deliverer_id") private UUID assignedDelivererId;
    @Column("assigned_vehicle_id") private UUID assignedVehicleId;
    @Column("status") private String status;
    @Column("scheduled_at") private Instant scheduledAt;
    @Column("started_at") private Instant startedAt;
    @Column("completed_at") private Instant completedAt;
    @Column("cancelled_at") private Instant cancelledAt;
    @Column("cancellation_reason") private String cancellationReason;
    @Column("quoted_amount") private BigDecimal quotedAmount;
    @Column("quoted_currency") private String quotedCurrency;
    @Column("branch_id") private UUID branchId;
    @Column("pickup_address") private String pickupAddress;
    @Column("delivery_address") private String deliveryAddress;
    @Column("sender_name") private String senderName;
    @Column("recipient_name") private String recipientName;
    @Column("recipient_phone") private String recipientPhone;
    @Column("weight_kg") private Double weightKg;
    @Column("distance_km") private Double distanceKm;
    @Column("packages_count") private Integer packagesCount;
    @Column("priority") private String priority;
    @Column("target_hub_id") private UUID targetHubId;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
    @Version @Column("version") private Long version;
}
