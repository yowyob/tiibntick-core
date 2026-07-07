package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code Delivery} aggregate.
 * Complex value objects (address, cost) are stored as JSONB columns.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_deliveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("announcement_id")
    private UUID announcementId;

    @Column("parcel_id")
    private UUID parcelId;

    @Column("sender_id")
    private UUID senderId;

    @Column("delivery_person_id")
    private UUID deliveryPersonId;

    @Column("tracking_code")
    private String trackingCode;

    @Column("status")
    private String status;

    @Column("urgency")
    private String urgency;

    // Pickup address (JSONB or flattened columns)
    @Column("pickup_street")
    private String pickupStreet;
    @Column("pickup_landmark")
    private String pickupLandmark;
    @Column("pickup_district")
    private String pickupDistrict;
    @Column("pickup_city")
    private String pickupCity;
    @Column("pickup_country")
    private String pickupCountry;
    @Column("pickup_latitude")
    private Double pickupLatitude;
    @Column("pickup_longitude")
    private Double pickupLongitude;

    // Delivery address
    @Column("delivery_street")
    private String deliveryStreet;
    @Column("delivery_landmark")
    private String deliveryLandmark;
    @Column("delivery_district")
    private String deliveryDistrict;
    @Column("delivery_city")
    private String deliveryCity;
    @Column("delivery_country")
    private String deliveryCountry;
    @Column("delivery_latitude")
    private Double deliveryLatitude;
    @Column("delivery_longitude")
    private Double deliveryLongitude;

    // Recipient
    @Column("recipient_name")
    private String recipientName;
    @Column("recipient_phone")
    private String recipientPhone;
    @Column("recipient_alt_phone")
    private String recipientAltPhone;

    // Cost
    @Column("estimated_cost_distance")
    private BigDecimal estimatedCostDistance;
    @Column("estimated_cost_time")
    private BigDecimal estimatedCostTime;
    @Column("estimated_cost_road")
    private BigDecimal estimatedCostRoad;
    @Column("estimated_cost_weather")
    private BigDecimal estimatedCostWeather;
    @Column("estimated_cost_fuel")
    private BigDecimal estimatedCostFuel;
    @Column("cost_currency")
    private String costCurrency;

    @Column("final_cost_total")
    private BigDecimal finalCostTotal;

    @Column("estimated_distance_km")
    private Double estimatedDistanceKm;

    // ETA
    @Column("eta_estimated_arrival")
    private Instant etaEstimatedArrival;
    @Column("eta_lower_bound")
    private Instant etaLowerBound;
    @Column("eta_upper_bound")
    private Instant etaUpperBound;
    @Column("eta_confidence")
    private Double etaConfidence;
    @Column("eta_remaining_minutes")
    private Integer etaRemainingMinutes;

    // Temporal
    @Column("scheduled_pickup_time")
    private Instant scheduledPickupTime;
    @Column("estimated_delivery_time")
    private Instant estimatedDeliveryTime;
    @Column("actual_pickup_time")
    private Instant actualPickupTime;
    @Column("actual_delivery_time")
    private Instant actualDeliveryTime;

    // ── Incident tracking (tnt-incident-core integration — ) ──────────────

    /** UUID of the incident blocking this delivery. Null when not paused by incident. */
    @Column("paused_by_incident_id")
    private UUID pausedByIncidentId;

    /** Status before being paused. Restored on incident resolution. */
    @Column("previous_status_before_pause")
    private String previousStatusBeforePause;

    /** Operational platform: GO | FREELANCER | POINT | AGENCY. */
    @Column("platform")
    @Builder.Default
    private String platform = "AGENCY";

    /** Agency UUID (null for freelancer/Go platform deliveries). */
    @Column("agency_id")
    private UUID agencyId;

    // ── FreelancerOrg integration () ──────────────────────────────────────────

    /** UUID of the FreelancerOrg executing this delivery. Null for Agency deliveries. */
    @Column("assigned_freelancer_org_id")
    private String assignedFreelancerOrgId;

    /** OWNER or SUB_DELIVERER role of the executing FreelancerOrg member. */
    @Column("assigned_freelancer_role")
    private String assignedFreelancerRole;

    /** UUID of the selected FreelancerVehicle from tnt-resource-core. */
    @Column("selected_vehicle_id")
    private String selectedVehicleId;

    /** JSON array of deployed FreelancerEquipment IDs. */
    @Column("active_equipment_ids_json")
    private String activeEquipmentIdsJson;

    /** Delivery attempt number (1 = first, 2 = re-delivery, etc.). */
    @Column("delivery_attempt_number")
    @Builder.Default
    private int deliveryAttemptNumber = 1;

    /** Whether active refrigeration is required for this delivery. */
    @Column("requires_refrigeration")
    @Builder.Default
    private boolean requiresRefrigeration = false;

    /** Whether assembly/installation at delivery is required. */
    @Column("requires_assembly")
    @Builder.Default
    private boolean requiresAssembly = false;

    /** Whether recipient identity check is required. */
    @Column("requires_id_check")
    @Builder.Default
    private boolean requiresIDCheck = false;

    @Column("notes")
    private String notes;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Long version;
}
