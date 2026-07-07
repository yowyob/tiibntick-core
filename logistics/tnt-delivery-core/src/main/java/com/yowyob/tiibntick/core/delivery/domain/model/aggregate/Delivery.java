package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.core.delivery.domain.event.*;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import com.yowyob.tiibntick.core.delivery.domain.policy.DeliveryStateTransitionPolicy;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Core aggregate root for TiiBnTick delivery lifecycle management.
 *
 * <p>Manages the full state machine:
 * CREATED → PICKED_UP → IN_TRANSIT → [AT_RELAY_POINT →] DELIVERED | FAILED | CANCELLED
 *
 * <p> — Added fields and methods for tnt-billing-core FreelancerOrg integration:
 * <ul>
 *   <li>{@link #assignedFreelancerOrgId} — the FreelancerOrg executing this delivery</li>
 *   <li>{@link #assignedFreelancerRole} — OWNER or SUB_DELIVERER role</li>
 *   <li>{@link #selectedVehicleId} — the FreelancerVehicle UUID from tnt-resource-core</li>
 *   <li>{@link #activeEquipmentIds} — equipment deployed for this mission</li>
 *   <li>{@link #deliveryAttemptNumber} — attempt number (for pricing context)</li>
 *   <li>{@link #requiresRefrigeration}, {@link #requiresAssembly}, {@link #requiresIDCheck}</li>
 * </ul>
 *
 * <p> — Added fields and methods for tnt-incident-core integration:
 * <ul>
 *   <li>{@link #agencyId} — the agency this delivery belongs to (null for freelancer/Go platforms)</li>
 *   <li>{@link #platform} — operational platform: GO | FREELANCER | POINT | AGENCY</li>
 *   <li>{@link #pausedByIncidentId} — UUID of the incident blocking this delivery</li>
 *   <li>{@link #previousStatusBeforePause} — status to restore after incident resolution</li>
 *   <li>{@link #pauseForIncident(UUID)} — transitions to PAUSED_BY_INCIDENT</li>
 *   <li>{@link #resumeFromIncident(UUID, UUID)} — restores previous status with new driver</li>
 * </ul>
 *
 * <p>Collects domain events that are dispatched by the application layer after persistence.
 * This keeps the domain free from infrastructure concerns (no Spring, no Kafka here).
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class Delivery {

    private final UUID id;
    private final UUID tenantId;
    private final UUID announcementId;

    // Parcel
    private final Parcel parcel;

    // Parties
    private final UUID senderId;
    private UUID deliveryPersonId;

    // Addresses
    private final DeliveryAddress pickupAddress;
    private final DeliveryAddress deliveryAddress;
    private final RecipientInfo recipient;

    // State
    private DeliveryStatus status;
    private final DeliveryUrgency urgency;

    // Financial
    private DeliveryCost estimatedCost;
    private DeliveryCost finalCost;
    private double estimatedDistanceKm;

    // Temporal
    private Instant scheduledPickupTime;
    private Instant estimatedDeliveryTime;
    private Instant actualPickupTime;
    private Instant actualDeliveryTime;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    // ETA
    private EtaEstimate currentEta;

    // Notes
    private String notes;

    /**
     * Agency UUID this delivery belongs to.
     * Null for freelancer deliveries or Go platform (no fixed agency).
     * Set at creation time from the announcement context.
     * Used in {@code MissionStatusChangedEvent} for tnt-incident-core.
     */
    private UUID agencyId;

    /**
     * Operational platform generating this delivery.
     * Values: "GO" | "FREELANCER" | "POINT" | "AGENCY"
     * Used in {@code MissionStatusChangedEvent} for tnt-incident-core auto-incident detection.
     */
    @Builder.Default
    private String platform = "AGENCY";

    /**
     * UUID of the incident currently blocking this delivery.
     * Set by {@code IMissionStatusPort.pauseMission()}.
     * Null when the delivery is not paused by an incident.
     */
    private UUID pausedByIncidentId;

    /**
     * The status this delivery had before being paused by an incident.
     * Restored by {@code IMissionStatusPort.resumeMission()} on incident resolution.
     * Null when not paused.
     */
    private DeliveryStatus previousStatusBeforePause;

    // ── FreelancerOrg integration (tnt-billing-core ) ────────────────────

    /**
     * UUID of the FreelancerOrganization executing this delivery.
     * References tnt-organization-core UUID — pure integration key (no join).
     * Null when the delivery is executed by an Agency (platform="AGENCY").
     * Set by {@code assignToFreelancerOrg()}.
     */
    private String assignedFreelancerOrgId;

    /**
     * Role of the FreelancerOrg member executing this delivery.
     * OWNER = the org founder executing directly.
     * SUB_DELIVERER = a partner deliverer invited by the OWNER.
     * Null when assignedFreelancerOrgId is null.
     */
    private FreelancerRole assignedFreelancerRole;

    /**
     * UUID of the FreelancerVehicle from tnt-resource-core selected for this mission.
     * Used by tnt-billing-cost to compute per-vehicle fuel costs.
     * Null when executed by Agency (uses agency fleet vehicle via VehicleAssignment).
     */
    private String selectedVehicleId;

    /**
     * IDs of the FreelancerEquipment from tnt-resource-core deployed for this mission.
     * Used by tnt-billing-cost to compute equipment additional costs.
     * Empty when no special equipment is used.
     */
    @Builder.Default
    private List<String> activeEquipmentIds = new ArrayList<>();

    /**
     * Number of delivery attempts for this mission (1 = first attempt, 2 = re-delivery, etc.).
     * Used by tnt-billing-pricing for the deliveryAttemptNumber DSL variable
     * (re-delivery surcharge).
     */
    @Builder.Default
    private int deliveryAttemptNumber = 1;

    /**
     * Whether this mission requires active refrigeration (cold chain).
     * Used by tnt-billing-pricing as the {@code requiresRefrigeration} DSL variable.
     * Derived from the parcel's PackageSpecification at announcement creation.
     */
    @Builder.Default
    private boolean requiresRefrigeration = false;

    /**
     * Whether the delivery requires assembly/installation at the recipient's location.
     * Used by tnt-billing-pricing as the {@code requiresAssembly} DSL variable.
     */
    @Builder.Default
    private boolean requiresAssembly = false;

    /**
     * Whether the delivery requires recipient ID verification (proof of identity).
     * Used by tnt-billing-pricing as the {@code requiresIDCheck} DSL variable.
     */
    @Builder.Default
    private boolean requiresIDCheck = false;

    // Domain events (not persisted — cleared after dispatch)
    @Builder.Default
    private final List<DeliveryDomainEvent> domainEvents = new ArrayList<>();

    // ── Factory methods ────────────────────────────────────────────────

    /**
     * Factory method for creating a new delivery request.
     */
    public static Delivery create(UUID tenantId,
                                   UUID announcementId,
                                   UUID senderId,
                                   Parcel parcel,
                                   DeliveryAddress pickupAddress,
                                   DeliveryAddress deliveryAddress,
                                   RecipientInfo recipient,
                                   DeliveryUrgency urgency,
                                   Instant scheduledPickupTime,
                                   String notes) {
        Delivery delivery = Delivery.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .announcementId(announcementId)
                .senderId(senderId)
                .parcel(parcel)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .recipient(recipient)
                .urgency(urgency)
                .status(DeliveryStatus.CREATED)
                .platform("AGENCY")
                .scheduledPickupTime(scheduledPickupTime)
                .notes(notes)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        delivery.domainEvents.add(new DeliveryCreatedEvent(delivery.id, tenantId, delivery.createdAt));
        return delivery;
    }

    // ── Domain mutations ───────────────────────────────────────────────

    /**
     * Assigns a delivery person and optionally sets estimated cost and distance.
     */
    public void assignDeliveryPerson(UUID deliveryPersonId,
                                      DeliveryCost estimatedCost,
                                      double estimatedDistanceKm,
                                      Instant estimatedDeliveryTime) {
        if (this.deliveryPersonId != null) {
            throw new DeliveryDomainException(
                "A delivery person is already assigned to delivery: " + id);
        }
        this.deliveryPersonId = deliveryPersonId;
        this.estimatedCost = estimatedCost;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.estimatedDeliveryTime = estimatedDeliveryTime;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryPersonAssignedEvent(id, tenantId, deliveryPersonId, updatedAt));
        addMissionStatusChangedEvent("CREATED", "CREATED");
    }

    /**
     * Records that the parcel has been physically collected by the delivery person.
     */
    public void confirmPickup() {
        String prev = status.name();
        validateTransition(DeliveryStatus.PICKED_UP);
        this.status = DeliveryStatus.PICKED_UP;
        this.actualPickupTime = Instant.now();
        this.updatedAt = Instant.now();

        domainEvents.add(new ParcelPickedUpEvent(id, tenantId, deliveryPersonId, actualPickupTime));
        addMissionStatusChangedEvent("PICKED_UP", prev);
    }

    /**
     * Starts transit — delivery person has left the pickup point.
     */
    public void startTransit(EtaEstimate eta) {
        String prev = status.name();
        validateTransition(DeliveryStatus.IN_TRANSIT);
        this.status = DeliveryStatus.IN_TRANSIT;
        this.currentEta = eta;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryInTransitEvent(id, tenantId, deliveryPersonId, eta, updatedAt));
        addMissionStatusChangedEvent("IN_TRANSIT", prev);
    }

    /**
     * Records a temporary stop at a relay/hub point.
     */
    public void depositAtRelayPoint(UUID relayPointId) {
        String prev = status.name();
        validateTransition(DeliveryStatus.AT_RELAY_POINT);
        this.status = DeliveryStatus.AT_RELAY_POINT;
        this.updatedAt = Instant.now();

        domainEvents.add(new ParcelAtRelayPointEvent(id, tenantId, relayPointId, updatedAt));
        addMissionStatusChangedEvent("AT_RELAY_POINT", prev);
    }

    /**
     * Resumes transit after a relay stop.
     */
    public void resumeFromRelayPoint(EtaEstimate updatedEta) {
        String prev = status.name();
        validateTransition(DeliveryStatus.IN_TRANSIT);
        this.status = DeliveryStatus.IN_TRANSIT;
        this.currentEta = updatedEta;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryInTransitEvent(id, tenantId, deliveryPersonId, updatedEta, updatedAt));
        addMissionStatusChangedEvent("IN_TRANSIT", prev);
    }

    /**
     * Updates real-time ETA using the extended Kalman filter output.
     */
    public void updateEta(EtaEstimate refinedEta) {
        if (!status.isActive()) {
            throw new DeliveryDomainException(
                "Cannot update ETA for delivery in terminal state: " + status);
        }
        this.currentEta = refinedEta;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks delivery as successfully completed.
     *
     * @param finalCost the actual cost charged (may differ from estimate)
     */
    public void complete(DeliveryCost finalCost) {
        String prev = status.name();
        validateTransition(DeliveryStatus.DELIVERED);
        this.status = DeliveryStatus.DELIVERED;
        this.finalCost = finalCost;
        this.actualDeliveryTime = Instant.now();
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryCompletedEvent(id, tenantId, deliveryPersonId,
                recipient, finalCost, actualDeliveryTime));
        addMissionStatusChangedEvent("DELIVERED", prev);
    }

    /**
     * Marks delivery as failed due to an incident.
     */
    public void fail(String reason) {
        String prev = status.name();
        validateTransition(DeliveryStatus.FAILED);
        this.status = DeliveryStatus.FAILED;
        this.notes = (notes == null ? "" : notes + "\n") + "FAILURE: " + reason;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryFailedEvent(id, tenantId, reason, updatedAt));
        addMissionStatusChangedEvent("FAILED", prev);
    }

    /**
     * Cancels the delivery before it has been picked up.
     */
    public void cancel(String reason) {
        String prev = status.name();
        validateTransition(DeliveryStatus.CANCELLED);
        this.status = DeliveryStatus.CANCELLED;
        this.notes = (notes == null ? "" : notes + "\n") + "CANCELLED: " + reason;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryCancelledEvent(id, tenantId, reason, updatedAt));
        addMissionStatusChangedEvent("CANCELLED", prev);
    }

    // ── FreelancerOrg assignment () ──────────────────────────────────

    /**
     * Assigns this delivery to a FreelancerOrganization for execution.
     *
     * <p>Called by {@code DeliveryLifecycleService.assignToFreelancerOrg()} when a client
     * selects a FreelancerOrg response to their delivery announcement.
     *
     * @param freelancerOrgId  the UUID of the FreelancerOrg executing this delivery
     * @param role             OWNER (executing directly) or SUB_DELIVERER (partner)
     */
    public void assignToFreelancerOrg(String freelancerOrgId, FreelancerRole role) {
        Objects.requireNonNull(freelancerOrgId, "freelancerOrgId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        this.assignedFreelancerOrgId = freelancerOrgId;
        this.assignedFreelancerRole = role;
        this.platform = "FREELANCER";
        this.updatedAt = Instant.now();
        domainEvents.add(new FreelancerOrgAssignedEvent(id, tenantId, freelancerOrgId, role.name(), updatedAt));
        addMissionStatusChangedEvent(status.name(), status.name()); // status unchanged, context updated
    }

    /**
     * Records the FreelancerVehicle and active equipment selected for this mission.
     *
     * <p>Called when tnt-resource-core confirms vehicle assignment via
     * {@code tnt.vehicle.assigned_to_mission} event.
     *
     * @param vehicleId    the UUID of the selected FreelancerVehicle
     * @param equipmentIds the IDs of deployed FreelancerEquipment
     */
    public void recordFreelancerVehicleAssigned(String vehicleId, List<String> equipmentIds) {
        this.selectedVehicleId = vehicleId;
        if (equipmentIds != null) {
            this.activeEquipmentIds = new ArrayList<>(equipmentIds);
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Increments the delivery attempt counter (for re-delivery surcharge pricing).
     * Called when a failed delivery is retried.
     */
    public void incrementDeliveryAttempt() {
        this.deliveryAttemptNumber++;
        this.updatedAt = Instant.now();
    }

    /**
     * Sets the parcel constraint flags from the announcement's package specification.
     * Called at Delivery creation time by DeliveryAnnouncementService.
     *
     * @param requiresRefrigeration whether active refrigeration is needed
     * @param requiresAssembly      whether assembly at delivery is needed
     * @param requiresIDCheck       whether recipient ID verification is needed
     */
    public void setParcelConstraints(boolean requiresRefrigeration,
                                      boolean requiresAssembly, boolean requiresIDCheck) {
        this.requiresRefrigeration = requiresRefrigeration;
        this.requiresAssembly = requiresAssembly;
        this.requiresIDCheck = requiresIDCheck;
        this.updatedAt = Instant.now();
    }

    // ── Incident integration (tnt-incident-core) ──────────────────────────────

    /**
     * Pauses this delivery due to an active incident.
     *
     * <p>Called by {@code MissionStatusPortAdapter.pauseMission()} when tnt-incident-core
     * needs to block the delivery while an incident is being resolved.
     * Saves the current status in {@link #previousStatusBeforePause} for later restoration.
     *
     * @param incidentId the UUID of the incident blocking this delivery
     */
    public void pauseForIncident(UUID incidentId) {
        Objects.requireNonNull(incidentId, "incidentId must not be null");
        if (status == DeliveryStatus.PAUSED_BY_INCIDENT) {
            throw new DeliveryDomainException(
                "Delivery " + id + " is already paused by incident " + pausedByIncidentId);
        }
        if (status.isTerminal()) {
            throw new DeliveryDomainException(
                "Cannot pause terminal delivery " + id + " (status=" + status + ")");
        }
        this.previousStatusBeforePause = this.status;
        validateTransition(DeliveryStatus.PAUSED_BY_INCIDENT);
        this.status = DeliveryStatus.PAUSED_BY_INCIDENT;
        this.pausedByIncidentId = incidentId;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryPausedByIncidentEvent(
                id, tenantId, incidentId, previousStatusBeforePause.name(), updatedAt));
        addMissionStatusChangedEvent("PAUSED_BY_INCIDENT", previousStatusBeforePause.name());
    }

    /**
     * Resumes this delivery after an incident was resolved.
     *
     * <p>Called by {@code MissionStatusPortAdapter.resumeMission()} when the incident
     * is resolved and a replacement driver (if any) has been assigned.
     * Restores the status from {@link #previousStatusBeforePause}.
     *
     * @param newDeliveryPersonId replacement driver UUID (may be same as current)
     * @param newVehicleId        replacement vehicle UUID (may be null if unchanged)
     */
    public void resumeFromIncident(UUID newDeliveryPersonId, UUID newVehicleId) {
        if (status != DeliveryStatus.PAUSED_BY_INCIDENT) {
            throw new DeliveryDomainException(
                "Delivery " + id + " is not paused by an incident (status=" + status + ")");
        }
        DeliveryStatus resumeTarget = previousStatusBeforePause != null
                ? previousStatusBeforePause : DeliveryStatus.PICKED_UP;

        validateTransition(resumeTarget);
        this.status = resumeTarget;

        // Update driver if a replacement was assigned
        if (newDeliveryPersonId != null) {
            this.deliveryPersonId = newDeliveryPersonId;
        }

        //UUID prevIncidentId = this.pausedByIncidentId;
        this.pausedByIncidentId = null;
        this.previousStatusBeforePause = null;
        this.updatedAt = Instant.now();

        domainEvents.add(new DeliveryResumedFromIncidentEvent(
                id, tenantId, newDeliveryPersonId, newVehicleId,
                resumeTarget.name(), updatedAt));
        addMissionStatusChangedEvent(resumeTarget.name(), "PAUSED_BY_INCIDENT");
    }

    // ── Domain event helpers ───────────────────────────────────────────

    /**
     * Returns an unmodifiable view of collected domain events.
     */
    public List<DeliveryDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears domain events after they have been dispatched.
     * Called by the application layer after successful persistence + publishing.
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * Sets the agency and platform context for this delivery.
     * Called after creation from the announcement service when the agency context is known.
     */
    public void setAgencyContext(UUID agencyId, String platform) {
        this.agencyId = agencyId;
        if (platform != null && !platform.isBlank()) {
            this.platform = platform;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void validateTransition(DeliveryStatus target) {
        if (!DeliveryStateTransitionPolicy.isAllowed(this.status, target)) {
            throw new InvalidDeliveryStateTransitionException(this.status, target);
        }
    }

    /**
     * Adds a {@link MissionStatusChangedEvent} to the domain events list.
     * This event is published to {@code tnt.delivery.mission.status-changed} and consumed
     * by tnt-incident-core's {@code IncidentEventConsumer} for SLA monitoring and
     * incident auto-detection.
     */
    private void addMissionStatusChangedEvent(String newSt, String prevSt) {
        UUID parcelId = parcel != null ? parcel.getId() : null;
        // : Include FreelancerOrg context when applicable
        if (assignedFreelancerOrgId != null) {
            domainEvents.add(new MissionStatusChangedEvent(
                    id, tenantId, newSt, prevSt,
                    deliveryPersonId, agencyId,
                    platform != null ? platform : "FREELANCER",
                    parcelId,
                    assignedFreelancerOrgId,
                    assignedFreelancerRole != null ? assignedFreelancerRole.name() : null,
                    updatedAt));
        } else {
            domainEvents.add(new MissionStatusChangedEvent(
                    id, tenantId, newSt, prevSt,
                    deliveryPersonId, agencyId,
                    platform != null ? platform : "AGENCY",
                    parcelId, updatedAt));
        }
    }
}
