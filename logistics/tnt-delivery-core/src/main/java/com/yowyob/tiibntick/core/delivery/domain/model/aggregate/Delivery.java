package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.domain.event.*;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryFrozenByDisputeException;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryAlertType;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RelayDeposit;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence.ParcelEffect;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine.*;

/**
 * A delivery: one trip of a parcel (outbound, return or redelivery) under the responsibility of one courier at a
 * time (spec §1.1, decisions [B], [C], [C bis]).
 *
 * <p>Every state change goes through {@link DeliveryStateMachine#TABLE} (rows D1–D21): a refused pair or guard throws
 * {@link InvalidDeliveryStateTransitionException} (409); while the parcel is under a dispute that freezes the delivery
 * (QO-7), every row except D20/D21 throws {@link DeliveryFrozenByDisputeException} (409). Each accepted transition
 * records exactly one {@code Delivery<PascalCase(target)>} {@link AggregateEvent} and applies, in the same operation,
 * the parcel transition given by {@link DeliveryParcelCorrespondence} (which records its own {@code Parcel…} event,
 * caused by the delivery event).
 *
 * <p>Lot L8: the legacy events of the old {@code tnt.delivery.*} topics are gone (G.5) — only the aggregate events
 * are recorded.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class Delivery implements EventSource {

    /** Default maximum of hand-over attempts when the thresholds are not given (spec §5, platform default). */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final UUID id;
    private final UUID tenantId;
    private final UUID announcementId;

    /** Nature of the delivery, fixed at creation [B.3]. */
    @Builder.Default
    private DeliveryKind kind = DeliveryKind.OUTBOUND;
    /** Origin of a RETURN or REDELIVERY, {@code null} for an OUTBOUND delivery. */
    private UUID parentDeliveryId;

    /**
     * Legacy per-delivery tracking code (pre D-136), kept only so that codes already printed keep resolving. The code
     * of record is the parcel's: see {@link #getTrackingCode()}.
     */
    private String legacyTrackingCode;

    private final Parcel parcel;

    private final UUID senderId;
    private UUID deliveryPersonId;

    private final DeliveryAddress pickupAddress;
    private final DeliveryAddress deliveryAddress;
    private final RecipientInfo recipient;

    private DeliveryStatus status;
    private final DeliveryUrgency urgency;

    private DeliveryCost estimatedCost;
    private DeliveryCost finalCost;
    private double estimatedDistanceKm;

    private Instant scheduledPickupTime;
    private Instant estimatedDeliveryTime;
    private Instant actualPickupTime;
    /** = {@code deliveredAt} of the payload. */
    private Instant actualDeliveryTime;
    private Instant failedAt;
    private Instant cancelledAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    private BigDecimal agreedAmount;
    private String agreedCurrency;
    private Instant pickupWindowStart;
    private Instant pickupWindowEnd;
    private Instant deliveryWindowStart;
    private Instant deliveryWindowEnd;

    private EtaEstimate currentEta;
    private String notes;

    private UUID agencyId;
    @Builder.Default
    private String platform = "AGENCY";

    /** Incident currently interrupting the delivery (payload {@code openIncidentId}), {@code null} otherwise. */
    private UUID pausedByIncidentId;
    /** Operational state memorised by D13/D15, restored by D14/D16/D18 (payload {@code previousStatusBeforeIncident}). */
    private DeliveryStatus previousStatusBeforePause;

    // ── attempts, failure, cancellation ───────────────────────────────────────────────────────

    /** Failed hand-over attempts (column {@code delivery_attempt_number}); starts at 0, incremented by D9/D10 only. */
    @Builder.Default
    private int attemptCount = 0;
    /** Maximum attempts read at the last attempt-related transition (spec §5). */
    @Builder.Default
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private DeliveryFailureReason failureReason;
    private DeliveryFailureReason lastAttemptReason;
    private String cancellationReason;
    private ResponsibleParty responsibleParty;
    private ResponsibleParty returnCostPayer;

    // ── relay hub ─────────────────────────────────────────────────────────────────────────────

    /** Relay hub where the parcel is (AT_RELAY_HUB) or was last deposited. */
    private UUID relayHubId;
    /** Relay hub that is the destination of the delivery, {@code null} when unknown (then any deposit is final). */
    private UUID destinationHubId;
    private UUID hubPackageEntryId;
    private Instant relayDepositedAt;
    private Integer requestedStorageDays;
    private Integer relayStorageDays;
    private Instant relayStorageExpiresAt;
    private Instant storageReminderAt;
    private Instant storageReminderSentAt;

    // ── lateness (alert, not a state [C.3]) ───────────────────────────────────────────────────

    private boolean late;
    private Instant lateSince;
    private String lastAlertType;
    /** Active alert types ({@link DeliveryAlertType} names), comma-separated; {@code null} = none (lot L3). */
    private String activeAlerts;
    /** Time of the last GPS position received for this delivery (lot L3, {@code GPS_SIGNAL_LOST}). */
    private Instant lastPositionAt;

    // ── courier legs (QO-11 / D-153) ──────────────────────────────────────────────────────────

    /** Where the current courier's leg started: the last hand-over point; {@code null} = the pickup address. */
    private Double legStartLatitude;
    private Double legStartLongitude;

    // ── return guarantee (D-147, D-151) ───────────────────────────────────────────────────────

    /** Return guarantee of a delivery to a relay hub, {@code null} when none is concerned. */
    private ReturnGuaranteeStatus returnGuaranteeStatus;
    /** Why the guarantee is {@code NOT_HANDLED} or {@code FAILED} (stable code), {@code null} otherwise. */
    private String returnGuaranteeReason;

    // ── FreelancerOrg ─────────────────────────────────────────────────────────────────────────

    private String assignedFreelancerOrgId;
    private FreelancerRole assignedFreelancerRole;
    private String selectedVehicleId;
    @Builder.Default
    private List<String> activeEquipmentIds = new ArrayList<>();
    @Builder.Default
    private boolean requiresRefrigeration = false;
    @Builder.Default
    private boolean requiresAssembly = false;
    @Builder.Default
    private boolean requiresIDCheck = false;

    @Builder.Default
    private final List<DeliveryDomainEvent> domainEvents = new ArrayList<>();

    /** Actor of the next transition when the caller knows it better than the row default. */
    private transient TntEventActor nextActor;

    // ── D1: creation ──────────────────────────────────────────────────────────────────────────

    /** Creates an OUTBOUND delivery (row D1). */
    public static Delivery create(UUID tenantId, UUID announcementId, UUID senderId, Parcel parcel,
                                  DeliveryAddress pickupAddress, DeliveryAddress deliveryAddress,
                                  RecipientInfo recipient, DeliveryUrgency urgency, Instant scheduledPickupTime,
                                  String notes) {
        return create(tenantId, announcementId, senderId, parcel, pickupAddress, deliveryAddress, recipient, urgency,
                scheduledPickupTime, notes, DeliveryKind.OUTBOUND, null, DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * Creates a delivery of any kind (row D1). The parcel is linked to it (at most one non-terminal delivery per
     * parcel) and keeps its state: the parcel moves with D2.
     */
    public static Delivery create(UUID tenantId, UUID announcementId, UUID senderId, Parcel parcel,
                                  DeliveryAddress pickupAddress, DeliveryAddress deliveryAddress,
                                  RecipientInfo recipient, DeliveryUrgency urgency, Instant scheduledPickupTime,
                                  String notes, DeliveryKind kind, UUID parentDeliveryId, int maxAttempts) {
        return create(null, tenantId, announcementId, senderId, parcel, pickupAddress, deliveryAddress, recipient,
                urgency, scheduledPickupTime, notes, kind, parentDeliveryId, maxAttempts);
    }

    /**
     * Same, with the id chosen by the caller (lot L5, spec §4.1: the escrow held at selection names the delivery
     * before it exists); {@code null} → a random id.
     */
    public static Delivery create(UUID id, UUID tenantId, UUID announcementId, UUID senderId, Parcel parcel,
                                  DeliveryAddress pickupAddress, DeliveryAddress deliveryAddress,
                                  RecipientInfo recipient, DeliveryUrgency urgency, Instant scheduledPickupTime,
                                  String notes, DeliveryKind kind, UUID parentDeliveryId, int maxAttempts) {
        Objects.requireNonNull(kind, "kind");
        if (kind != DeliveryKind.OUTBOUND && parentDeliveryId == null) {
            throw new DeliveryDomainException("A " + kind + " delivery needs its parent delivery");
        }
        Instant now = Instant.now();
        Delivery delivery = Delivery.builder()
                .id(id != null ? id : UUID.randomUUID())
                .tenantId(tenantId)
                .announcementId(announcementId)
                .kind(kind)
                .parentDeliveryId(parentDeliveryId)
                .senderId(senderId)
                .parcel(parcel)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .recipient(recipient)
                .urgency(urgency)
                .status(DeliveryStatus.CREATED)
                .maxAttempts(maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS)
                .platform("AGENCY")
                .scheduledPickupTime(scheduledPickupTime)
                .notes(notes)
                .createdAt(now)
                .updatedAt(now)
                .build();
        if (parcel != null) {
            parcel.attachDelivery(delivery.id);
        }
        delivery.record(D1, null, Map.of(), "CREATE_DELIVERY", TntEventActor.of(TntEventActor.Type.SENDER,
                senderId), Map.of());
        return delivery;
    }

    /** D-136: the tracking code is the parcel's; the legacy per-delivery code only when the parcel has none. */
    public String getTrackingCode() {
        if (parcel != null && parcel.getTrackingCode() != null) {
            return parcel.getTrackingCode();
        }
        return legacyTrackingCode;
    }

    /** Sets the actor of the next transition (e.g. RELAY_OP for a counter collection, SUPPORT for a cancellation). */
    public Delivery actingAs(TntEventActor actor) {
        this.nextActor = actor;
        return this;
    }

    // ── D2–D12 ────────────────────────────────────────────────────────────────────────────────

    /** Estimate computed when a response is selected, before D2 (no transition, no event). */
    public void recordSelectionEstimate(DeliveryCost cost, double distanceKm, Instant estimatedDelivery) {
        this.estimatedCost = cost;
        this.estimatedDistanceKm = distanceKm;
        this.estimatedDeliveryTime = estimatedDelivery;
    }

    /** Row D2: a courier (checked APPROVED by the caller) becomes responsible for the delivery. */
    public void assignDeliveryPerson(UUID deliveryPersonId, DeliveryCost estimatedCost, double estimatedDistanceKm,
                                     Instant estimatedDeliveryTime) {
        Objects.requireNonNull(deliveryPersonId, "deliveryPersonId");
        if (this.deliveryPersonId != null) {
            throw new DeliveryDomainException("A delivery person is already assigned to delivery: " + id);
        }
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("courierId", null);
        guard(D2, DeliveryStatus.ASSIGNED);
        this.deliveryPersonId = deliveryPersonId;
        this.estimatedCost = estimatedCost;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.estimatedDeliveryTime = estimatedDeliveryTime;
        apply(D2, DeliveryStatus.ASSIGNED, old, "ASSIGN_COURIER", sender(), Map.of());
    }

    /** Row D3: cancellation, reason required (QO-6). */
    public void cancel(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidDeliveryStateTransitionException(status, DeliveryStatus.CANCELLED,
                    "a cancellation reason is required (QO-6)");
        }
        guard(D3, DeliveryStatus.CANCELLED);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("cancellationReason", cancellationReason);
        this.cancellationReason = reason;
        this.cancelledAt = Instant.now();
        this.notes = (notes == null ? "" : notes + "\n") + "CANCELLED: " + reason;
        TntEventActor actor = takeActor(sender());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("cancelledBy", actor.type().name());
        apply(D3, DeliveryStatus.CANCELLED, old, "CANCEL_DELIVERY", actor, details);
    }

    /** Row D4: the courier collected the parcel. */
    public void confirmPickup() {
        guard(D4, DeliveryStatus.PICKED_UP);
        this.actualPickupTime = Instant.now();
        apply(D4, DeliveryStatus.PICKED_UP, Map.of(), "CONFIRM_PICKUP", courier(), Map.of());
    }

    /** Row D5: the courier left the pickup point. */
    public void startTransit(EtaEstimate eta) {
        guard(D5, DeliveryStatus.IN_TRANSIT);
        this.currentEta = eta;
        apply(D5, DeliveryStatus.IN_TRANSIT, Map.of(), "START_TRANSIT", courier(), Map.of());
    }

    /** Row D6: the parcel leaves a relay hub towards another relay. */
    public void resumeFromRelayHub(EtaEstimate updatedEta) {
        guard(D6, DeliveryStatus.IN_TRANSIT);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("relayHubId", relayHubId);
        this.currentEta = updatedEta;
        this.relayHubId = null;
        this.hubPackageEntryId = null;
        clearStorage();
        apply(D6, DeliveryStatus.IN_TRANSIT, old, "RESUME_FROM_RELAY_HUB", courier(), Map.of());
    }

    /** Row D7: the parcel is deposited at a relay hub; storage starts when the hub is the destination. */
    public void depositAtRelayHub(RelayDeposit deposit) {
        Objects.requireNonNull(deposit, "deposit");
        guard(D7, DeliveryStatus.AT_RELAY_HUB);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("relayHubId", relayHubId);
        this.relayHubId = deposit.hubId();
        this.hubPackageEntryId = deposit.hubPackageEntryId();
        this.relayDepositedAt = deposit.depositedAt();
        this.requestedStorageDays = deposit.requestedStorageDays();
        this.relayStorageDays = deposit.relayStorageDays();
        this.relayStorageExpiresAt = deposit.storageExpiresAt();
        this.storageReminderAt = deposit.reminderAt();
        this.storageReminderSentAt = null;
        // lot L5 (QO-10): the escrow is released at the deposit when this hub is the destination
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("atDestination", destinationHubId == null || destinationHubId.equals(deposit.hubId()));
        apply(D7, DeliveryStatus.AT_RELAY_HUB, old, "DEPOSIT_AT_RELAY_HUB", takeActor(courier()), details);
    }

    /**
     * Records the state of the return guarantee (D-147, D-151) as decided by the application after the wallet
     * operation; not a transition, no event of its own.
     */
    public void recordReturnGuarantee(ReturnGuaranteeStatus status, String reason) {
        this.returnGuaranteeStatus = status;
        this.returnGuaranteeReason = reason;
        this.updatedAt = Instant.now();
    }

    /** @return whether the current relay hub is the destination of the delivery (storage and D12 apply) */
    public boolean isAtDestinationHub() {
        return status == DeliveryStatus.AT_RELAY_HUB
                && (destinationHubId == null || destinationHubId.equals(relayHubId));
    }

    /** Row D8: last leg towards the recipient (from a relay, or a new attempt after a failed one). */
    public void startFinalLeg(EtaEstimate eta, int currentMaxAttempts) {
        guard(D8, DeliveryStatus.OUT_FOR_DELIVERY);
        if (currentMaxAttempts > 0) {
            this.maxAttempts = currentMaxAttempts;
        }
        if (status == DeliveryStatus.DELIVERY_ATTEMPT_FAILED && attemptCount >= maxAttempts) {
            throw new InvalidDeliveryStateTransitionException(status, DeliveryStatus.OUT_FOR_DELIVERY,
                    "attemptCount " + attemptCount + " >= maxAttempts " + maxAttempts);
        }
        Map<String, Object> old = new LinkedHashMap<>();
        if (status == DeliveryStatus.AT_RELAY_HUB) {
            old.put("relayHubId", relayHubId);
            this.relayHubId = null;
            this.hubPackageEntryId = null;
            clearStorage();
        }
        this.currentEta = eta;
        apply(D8, DeliveryStatus.OUT_FOR_DELIVERY, old, "START_FINAL_LEG", courier(), Map.of());
    }

    /**
     * Rows D9/D10: a hand-over attempt failed. The counter is incremented; the delivery fails (D10) on a refusal by
     * the recipient or when the maximum is reached, otherwise another attempt is possible (D9).
     *
     * @param reason            attempt reason ({@link DeliveryFailureReason#isAttemptReason()})
     * @param currentMaxAttempts maximum read from the thresholds at the time of the transition (≤ 0: keep)
     * @return the state reached
     */
    public DeliveryStatus recordFailedAttempt(DeliveryFailureReason reason, int currentMaxAttempts) {
        Objects.requireNonNull(reason, "reason");
        if (!reason.isAttemptReason()) {
            throw new DeliveryDomainException("Not an attempt reason: " + reason);
        }
        if (currentMaxAttempts > 0) {
            this.maxAttempts = currentMaxAttempts;
        }
        int next = attemptCount + 1;
        boolean ends = reason.endsDeliveryAtOnce() || next >= maxAttempts;
        DeliveryStatus target = ends ? DeliveryStatus.FAILED : DeliveryStatus.DELIVERY_ATTEMPT_FAILED;
        String row = ends ? D10 : D9;
        guard(row, target);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("attemptCount", attemptCount);
        old.put("lastAttemptReason", name(lastAttemptReason));
        this.attemptCount = next;
        this.lastAttemptReason = reason;
        Map<String, Object> details = new LinkedHashMap<>();
        if (ends) {
            old.put("failureReason", name(failureReason));
            this.failureReason = reason.endsDeliveryAtOnce() ? reason : DeliveryFailureReason.MAX_ATTEMPTS_REACHED;
            this.responsibleParty = ResponsibleParty.RECIPIENT;
            this.failedAt = Instant.now();
            details.put("reason", failureReason.name());
            details.put("responsibleParty", responsibleParty.name());
        } else {
            details.put("reason", reason.name());
            details.put("attemptNumber", attemptCount);
        }
        apply(row, target, old, "RECORD_FAILED_ATTEMPT", courier(), details);
        if (ends) {
        }
        return target;
    }

    /** Row D11: handed over (OTP checked by the caller) or collected at the relay counter (collection code). */
    public void complete(DeliveryCost finalCost) {
        guard(D11, DeliveryStatus.DELIVERED);
        this.finalCost = finalCost;
        this.actualDeliveryTime = Instant.now();
        TntEventActor actor = takeActor(status == DeliveryStatus.AT_RELAY_HUB
                ? TntEventActor.of(TntEventActor.Type.RELAY_OP, null) : courier());
        apply(D11, DeliveryStatus.DELIVERED, Map.of(), "COMPLETE_DELIVERY", actor, Map.of());
    }

    /** Row D12: the parcel was not collected at the destination relay before the end of storage. */
    public void expireRelayStorage() {
        if (!isAtDestinationHub()) {
            throw new InvalidDeliveryStateTransitionException(status, DeliveryStatus.FAILED,
                    "the relay hub is not the destination of the delivery");
        }
        guard(D12, DeliveryStatus.FAILED);
        failWith(D12, DeliveryFailureReason.NOT_COLLECTED_AT_RELAY, ResponsibleParty.RECIPIENT,
                "RELAY_STORAGE_EXPIRED", TntEventActor.system());
    }

    // ── D13–D20: incidents ────────────────────────────────────────────────────────────────────

    /** Row D13: the courier declared an incident (transient state until triage, QO-1). */
    public void markIncidentReported(UUID incidentId) {
        Objects.requireNonNull(incidentId, "incidentId");
        guard(D13, DeliveryStatus.INCIDENT_REPORTED);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("openIncidentId", pausedByIncidentId);
        old.put("previousStatusBeforeIncident", name(previousStatusBeforePause));
        this.previousStatusBeforePause = status;
        this.pausedByIncidentId = incidentId;
        apply(D13, DeliveryStatus.INCIDENT_REPORTED, old, "INCIDENT_REPORTED", courier(),
                Map.of("incidentId", incidentId.toString()));
    }

    /** Row D15: a blocking incident stops the delivery (from INCIDENT_REPORTED or an operational state). */
    public void pauseForIncident(UUID incidentId) {
        Objects.requireNonNull(incidentId, "incidentId");
        guard(D15, DeliveryStatus.PAUSED_BY_INCIDENT);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("openIncidentId", pausedByIncidentId);
        if (status != DeliveryStatus.INCIDENT_REPORTED) {
            old.put("previousStatusBeforeIncident", name(previousStatusBeforePause));
            this.previousStatusBeforePause = status;
        }
        this.pausedByIncidentId = incidentId;
        DeliveryStatus memorised = previousStatusBeforePause;
        apply(D15, DeliveryStatus.PAUSED_BY_INCIDENT, old, "INCIDENT_BLOCKING", takeActor(TntEventActor.system()),
                Map.of("incidentId", incidentId.toString()));
    }

    /**
     * Rows D14, D16, D18: back to the memorised operational state.
     *
     * @param trigger             {@code INCIDENT_NOT_BLOCKING}, {@code INCIDENT_CANCELLED}, {@code INCIDENT_RESOLVED}
     *                            or {@code HANDOVER_COMPLETED}
     * @param newDeliveryPersonId replacement courier — required after a hand-over (D18), optional otherwise
     * @param newVehicleId        replacement vehicle, may be {@code null}
     * @return the state restored
     */
    public DeliveryStatus resumeAfterIncident(String trigger, UUID newDeliveryPersonId, UUID newVehicleId) {
        return resumeAfterIncident(trigger, newDeliveryPersonId, newVehicleId, null);
    }

    /**
     * Same as {@link #resumeAfterIncident(String, UUID, UUID)} with the hand-over point (QO-11 / D-153). When the
     * courier changes (D18), the event's {@code details} carry the courier legs that pay the replacement at the prorata
     * of the distance: {@code replacedCourierId}, {@code replacementCourierId}, {@code replacedLegKm} (straight line from
     * the start of the leaving courier's leg — the pickup, or the previous hand-over — to this point) and
     * {@code remainingKm} (from this point to the delivery address). A distance is {@code null} when a point is
     * unknown; the point becomes the start of the replacement's leg.
     *
     * @param handoverPoint where the parcel changed hands, may be {@code null}
     */
    public DeliveryStatus resumeAfterIncident(String trigger, UUID newDeliveryPersonId, UUID newVehicleId,
                                              GeoCoordinates handoverPoint) {
        String row = switch (status) {
            case INCIDENT_REPORTED -> D14;
            case PAUSED_BY_INCIDENT -> D16;
            case INCIDENT_HANDOVER -> D18;
            default -> throw new InvalidDeliveryStateTransitionException(status, previousStatusBeforePause,
                    "the delivery is not interrupted by an incident");
        };
        DeliveryStatus target = previousStatusBeforePause;
        if (target == null || !DeliveryStateMachine.TABLE.isAllowed(status, target)) {
            throw new InvalidDeliveryStateTransitionException(status, target, "target must be the memorised state");
        }
        if (row.equals(D18) && newDeliveryPersonId == null) {
            throw new InvalidDeliveryStateTransitionException(status, target,
                    "a hand-over needs the replacement courier");
        }
        guard(row, target);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("openIncidentId", pausedByIncidentId);
        old.put("previousStatusBeforeIncident", name(previousStatusBeforePause));
        UUID previousCourier = deliveryPersonId;
        if (newDeliveryPersonId != null && !newDeliveryPersonId.equals(deliveryPersonId)) {
            old.put("courierId", deliveryPersonId);
            this.deliveryPersonId = newDeliveryPersonId;
        }
        if (newVehicleId != null) {
            old.put("vehicleId", selectedVehicleId);
            this.selectedVehicleId = newVehicleId.toString();
        }
        this.pausedByIncidentId = null;
        this.previousStatusBeforePause = null;
        String resolvedTrigger = row.equals(D18) ? "HANDOVER_COMPLETED"
                : (trigger == null || trigger.isBlank() ? (row.equals(D14) ? "INCIDENT_NOT_BLOCKING" : "INCIDENT_RESOLVED")
                : trigger);
        Map<String, Object> details = previousCourier == null || Objects.equals(previousCourier, deliveryPersonId)
                ? Map.of()
                : row.equals(D18) || target.courierHoldsParcel() ? courierLegs(previousCourier, handoverPoint)
                : courierLegsBeforePickup(previousCourier);
        AggregateEvent event = apply(row, target, old, resolvedTrigger, takeActor(TntEventActor.system()), details);
        if (row.equals(D18) && parcel != null && target.courierHoldsParcel()
                && !Objects.equals(previousCourier, deliveryPersonId)) {
            parcel.changeCustody(ParcelCustody.courier(deliveryPersonId), "HANDOVER_COMPLETED", "HANDOVER",
                    event.actor(), event.eventId(), tenantId);
        }
        return target;
    }

    /** QO-11: the leg of the courier who leaves, and the point where the replacement's leg starts. */
    private Map<String, Object> courierLegs(UUID replacedCourier, GeoCoordinates handoverPoint) {
        GeoCoordinates start = legStartLatitude != null && legStartLongitude != null
                ? new GeoCoordinates(legStartLatitude, legStartLongitude)
                : pickupAddress != null ? pickupAddress.coordinates() : null;
        GeoCoordinates end = deliveryAddress != null ? deliveryAddress.coordinates() : null;
        Map<String, Object> legs = new LinkedHashMap<>();
        legs.put("replacedCourierId", replacedCourier.toString());
        legs.put("replacementCourierId", deliveryPersonId.toString());
        legs.put("replacedLegKm", handoverPoint != null && start != null ? km(start, handoverPoint) : null);
        legs.put("remainingKm", handoverPoint != null && end != null ? km(handoverPoint, end) : null);
        if (handoverPoint != null) {
            this.legStartLatitude = handoverPoint.latitude();
            this.legStartLongitude = handoverPoint.longitude();
        }
        return legs;
    }

    /** QO-11: replaced before the pickup, the leaving courier carried nothing (0 km); the replacement does it all. */
    private Map<String, Object> courierLegsBeforePickup(UUID replacedCourier) {
        GeoCoordinates start = pickupAddress != null ? pickupAddress.coordinates() : null;
        GeoCoordinates end = deliveryAddress != null ? deliveryAddress.coordinates() : null;
        Map<String, Object> legs = new LinkedHashMap<>();
        legs.put("replacedCourierId", replacedCourier.toString());
        legs.put("replacementCourierId", deliveryPersonId.toString());
        legs.put("replacedLegKm", java.math.BigDecimal.ZERO.setScale(3));
        legs.put("remainingKm", start != null && end != null ? km(start, end) : null);
        return legs;
    }

    private static java.math.BigDecimal km(GeoCoordinates from, GeoCoordinates to) {
        return java.math.BigDecimal.valueOf(from.haversineDistanceTo(to)).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    /** Row D17: a replacement courier takes the parcel over (the memorised state is one where the courier holds it). */
    public void startHandover() {
        if (status == DeliveryStatus.PAUSED_BY_INCIDENT
                && !DeliveryStateMachine.HANDOVER_SOURCES.contains(previousStatusBeforePause)) {
            throw new InvalidDeliveryStateTransitionException(status, DeliveryStatus.INCIDENT_HANDOVER,
                    "the courier does not hold the parcel (memorised " + previousStatusBeforePause + ")");
        }
        guard(D17, DeliveryStatus.INCIDENT_HANDOVER);
        apply(D17, DeliveryStatus.INCIDENT_HANDOVER, Map.of(), "START_HANDOVER", takeActor(TntEventActor.system()),
                Map.of());
    }

    /** Row D19: the hand-over is aborted; the delivery stays paused. */
    public void abortHandover() {
        guard(D19, DeliveryStatus.PAUSED_BY_INCIDENT);
        apply(D19, DeliveryStatus.PAUSED_BY_INCIDENT, Map.of(), "HANDOVER_ABORTED", takeActor(TntEventActor.system()),
                Map.of());
    }

    /**
     * Row D20: the incident ends without outcome. The parcel goes back to its sender if it was collected, otherwise
     * it is free again (QO-5).
     */
    public void failAfterIncident(ResponsibleParty responsible) {
        guard(D20, DeliveryStatus.FAILED);
        failWith(D20, DeliveryFailureReason.INCIDENT_UNRESOLVED, responsible, "INCIDENT_UNRESOLVED",
                takeActor(TntEventActor.system()));
    }

    /** Row D21: the parcel was declared lost or damaged by a dispute; the delivery ends. */
    public void failForParcelLoss(DeliveryFailureReason reason, ResponsibleParty responsible) {
        if (reason != DeliveryFailureReason.PARCEL_LOST && reason != DeliveryFailureReason.PARCEL_DAMAGED) {
            throw new DeliveryDomainException("D21 needs PARCEL_LOST or PARCEL_DAMAGED, got " + reason);
        }
        guard(D21, DeliveryStatus.FAILED);
        failWith(D21, reason, responsible, reason.name(), takeActor(TntEventActor.system()));
    }

    private void failWith(String row, DeliveryFailureReason reason, ResponsibleParty responsible, String trigger,
                          TntEventActor actor) {
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("failureReason", name(failureReason));
        this.failureReason = reason;
        this.responsibleParty = responsible;
        this.failedAt = Instant.now();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name());
        details.put("responsibleParty", name(responsible));
        apply(row, DeliveryStatus.FAILED, old, trigger, actor, details);
    }

    // ── changes without transition ────────────────────────────────────────────────────────────

    /** {@code DeliveryEtaUpdated}: a new ETA (not in a final state). */
    public void updateEta(EtaEstimate refinedEta) {
        if (status.isTerminal()) {
            throw new DeliveryDomainException("Cannot update ETA for delivery in terminal state: " + status);
        }
        Instant previousEta = currentEta != null ? currentEta.estimatedArrival() : null;
        this.currentEta = refinedEta;
        this.updatedAt = Instant.now();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousEta", previousEta == null ? null : previousEta.toString());
        details.put("eta", refinedEta == null || refinedEta.estimatedArrival() == null ? null
                : refinedEta.estimatedArrival().toString());
        recordChange("ETA_UPDATED", Map.of(), "UPDATE_ETA", courier(), details);
    }

    /**
     * {@code DeliveryAlertRaised(RELAY_STORAGE_EXPIRING)}: the storage reminder, sent once (spec §5.2).
     *
     * @return whether the reminder was recorded (false when already sent)
     */
    public boolean raiseStorageReminder(Instant now) {
        if (storageReminderSentAt != null || !isAtDestinationHub()) {
            return false;
        }
        this.storageReminderSentAt = now;
        this.updatedAt = now;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("alertType", "RELAY_STORAGE_EXPIRING");
        details.put("observedValue", relayStorageExpiresAt == null ? null : relayStorageExpiresAt.toString());
        details.put("threshold", relayStorageDays);
        recordChange("ALERT_RAISED", Map.of(), "RELAY_STORAGE_REMINDER", TntEventActor.system(), details);
        return true;
    }

    // ── delivery alerts (lot L3, spec §3.5: detections are alerts, never incidents [D.1]) ─────────

    /** @return the active alert types, in raising order */
    public java.util.List<String> activeAlertList() {
        if (activeAlerts == null || activeAlerts.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(activeAlerts.split(","));
    }

    /** @return whether {@code type} is currently raised */
    public boolean hasActiveAlert(DeliveryAlertType type) {
        return activeAlertList().contains(type.name());
    }

    /**
     * {@code DeliveryAlertRaised}: a detection on this delivery (no transition). Raised once while active; the
     * lateness alerts ({@link DeliveryAlertType#isLateness()}) also set {@code late} for good (lateness is a flag,
     * not a state [C.3]).
     *
     * @return whether the alert was raised (false when the delivery is terminal or the alert already active)
     */
    public boolean raiseAlert(DeliveryAlertType type, Object observedValue, Object threshold, Instant now) {
        if (status.isTerminal() || hasActiveAlert(type)) {
            return false;
        }
        java.util.List<String> active = new java.util.ArrayList<>(activeAlertList());
        active.add(type.name());
        this.activeAlerts = String.join(",", active);
        this.lastAlertType = type.name();
        if (type.isLateness() && !late) {
            this.late = true;
            this.lateSince = now;
        }
        this.updatedAt = now;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("alertType", type.name());
        details.put("observedValue", observedValue == null ? null : observedValue.toString());
        details.put("threshold", threshold == null ? null : threshold.toString());
        recordChange("ALERT_RAISED", Map.of(), "ALERT_" + type.name(), TntEventActor.system(), details);
        return true;
    }

    /**
     * {@code DeliveryAlertCleared}: the condition of an active alert is over (GPS back, courier moving again…).
     * Lateness stays recorded in {@code late}/{@code lateSince}.
     *
     * @return whether an active alert was cleared
     */
    public boolean clearAlert(DeliveryAlertType type, Instant now) {
        if (!hasActiveAlert(type)) {
            return false;
        }
        java.util.List<String> active = new java.util.ArrayList<>(activeAlertList());
        active.remove(type.name());
        this.activeAlerts = active.isEmpty() ? null : String.join(",", active);
        this.updatedAt = now;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("alertType", type.name());
        recordChange("ALERT_CLEARED", Map.of(), "ALERT_CLEARED", TntEventActor.system(), details);
        return true;
    }

    /** Records the time of a GPS position of the courier (no event). */
    public void recordPosition(Instant at) {
        if (at != null && (lastPositionAt == null || at.isAfter(lastPositionAt))) {
            this.lastPositionAt = at;
        }
    }

    /** Records the terms agreed for the mission (no transition, no event). */
    public void recordAgreedTerms(BigDecimal amount, String currency, Instant pickupWindowStart,
                                  Instant pickupWindowEnd, Instant deliveryWindowStart, Instant deliveryWindowEnd) {
        if (status.isTerminal()) {
            throw new DeliveryDomainException(
                    "Cannot record agreed terms on terminal delivery " + id + " (status=" + status + ")");
        }
        if (amount != null && amount.signum() < 0) {
            throw new DeliveryDomainException("Agreed amount must not be negative, got: " + amount);
        }
        requireOrderedWindow("pickup", pickupWindowStart, pickupWindowEnd);
        requireOrderedWindow("delivery", deliveryWindowStart, deliveryWindowEnd);
        if (amount != null) {
            this.agreedAmount = amount;
            this.agreedCurrency = currency;
        }
        this.pickupWindowStart = pickupWindowStart;
        this.pickupWindowEnd = pickupWindowEnd;
        this.deliveryWindowStart = deliveryWindowStart;
        this.deliveryWindowEnd = deliveryWindowEnd;
        this.updatedAt = Instant.now();
    }

    private static void requireOrderedWindow(String name, Instant start, Instant end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new DeliveryDomainException(
                    "The " + name + " window ends (" + end + ") before it starts (" + start + ")");
        }
    }

    /** {@code DeliveryFreelancerOrgAssigned}: the FreelancerOrg executing the delivery. */
    public void assignToFreelancerOrg(String freelancerOrgId, FreelancerRole role) {
        Objects.requireNonNull(freelancerOrgId, "freelancerOrgId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("freelancerOrgId", assignedFreelancerOrgId);
        old.put("platform", platform);
        this.assignedFreelancerOrgId = freelancerOrgId;
        this.assignedFreelancerRole = role;
        this.platform = "FREELANCER";
        this.updatedAt = Instant.now();
        recordChange("FREELANCER_ORG_ASSIGNED", old, "ASSIGN_FREELANCER_ORG", sender(),
                Map.of("role", role.name()));
    }

    /** {@code DeliveryVehicleChanged}: the vehicle and equipment of the mission. */
    public void recordFreelancerVehicleAssigned(String vehicleId, List<String> equipmentIds) {
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("vehicleId", selectedVehicleId);
        this.selectedVehicleId = vehicleId;
        if (equipmentIds != null) {
            this.activeEquipmentIds = new ArrayList<>(equipmentIds);
        }
        this.updatedAt = Instant.now();
        recordChange("VEHICLE_CHANGED", old, "ASSIGN_VEHICLE", courier(), Map.of());
    }

    public void setParcelConstraints(boolean requiresRefrigeration, boolean requiresAssembly,
                                     boolean requiresIDCheck) {
        this.requiresRefrigeration = requiresRefrigeration;
        this.requiresAssembly = requiresAssembly;
        this.requiresIDCheck = requiresIDCheck;
        this.updatedAt = Instant.now();
    }

    public void setAgencyContext(UUID agencyId, String platform) {
        this.agencyId = agencyId;
        if (platform != null && !platform.isBlank()) {
            this.platform = platform;
        }
    }

    /** Relay hub that is the destination of the delivery (D12 applies only there). */
    public void setDestinationHub(UUID destinationHubId) {
        this.destinationHubId = destinationHubId;
    }

    /** Pricing alias: number of failed hand-over attempts so far. */
    public int getDeliveryAttemptNumber() {
        return attemptCount;
    }

    // ── events ────────────────────────────────────────────────────────────────────────────────

    /** Events of the delivery, then those of its parcel (each caused by a delivery event), in order. */
    @Override
    public List<DeliveryDomainEvent> getDomainEvents() {
        if (parcel == null || parcel.getDomainEvents().isEmpty()) {
            return Collections.unmodifiableList(domainEvents);
        }
        List<DeliveryDomainEvent> all = new ArrayList<>(domainEvents);
        all.addAll(parcel.getDomainEvents());
        return Collections.unmodifiableList(all);
    }

    /** @return the lifecycle events of the delivery itself (no legacy, no parcel event) */
    public List<AggregateEvent> getAggregateEvents() {
        return domainEvents.stream().filter(AggregateEvent.class::isInstance).map(AggregateEvent.class::cast)
                .toList();
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
        if (parcel != null) {
            parcel.clearDomainEvents();
        }
    }

    /** Called by the persistence adapter once saved, with the version written. */
    public void markPersisted(Long persistedVersion) {
        this.version = persistedVersion;
    }

    /** @return the unmasked snapshot of the delivery (spec §3.6) */
    public DeliverySnapshot snapshot() {
        return new DeliverySnapshot(id, parcel != null ? parcel.getId() : null, kind, parentDeliveryId, status,
                previousStatusBeforePause, announcementId, agencyId, platform, senderId, recipient,
                deliveryPersonId, selectedVehicleId, assignedFreelancerOrgId, pickupAddress, deliveryAddress,
                relayHubId, pickupWindowStart, pickupWindowEnd, deliveryWindowStart, deliveryWindowEnd,
                currentEta != null ? currentEta.estimatedArrival() : null, attemptCount, maxAttempts,
                failureReason, lastAttemptReason, cancellationReason, returnCostPayer, late, lateSince,
                lastAlertType, pausedByIncidentId, parcel != null && parcel.isFrozenByDispute(), agreedAmount,
                agreedCurrency, relayStorageExpiresAt, createdAt, updatedAt, actualDeliveryTime, failedAt,
                cancelledAt, activeAlertList());
    }

    // ── internals ─────────────────────────────────────────────────────────────────────────────

    /** Checks that {@code row} allows {@code status → target} and that no dispute freezes the delivery. */
    private void guard(String row, DeliveryStatus target) {
        if (!DeliveryStateMachine.TABLE.rowsFor(status, target).stream().anyMatch(r -> r.id().equals(row))) {
            throw new InvalidDeliveryStateTransitionException(status, target, "not allowed by row " + row);
        }
        if (!row.equals(D20) && !row.equals(D21) && parcel != null && parcel.isFrozenByDispute()) {
            throw new DeliveryFrozenByDisputeException(id, parcel.getOpenDisputeId());
        }
    }

    /**
     * Applies a checked transition: state, event, parcel effect (same operation), parcel release when final.
     *
     * @return the delivery event recorded
     */
    private AggregateEvent apply(String row, DeliveryStatus target, Map<String, Object> old, String trigger,
                                 TntEventActor actor, Map<String, Object> details) {
        DeliveryStatus before = status;
        DeliveryStatus operational = before.isIncidentState() ? previousStatusBeforePause : before;
        if (before.isIncidentState() && operational == null) {
            operational = old.get("previousStatusBeforeIncident") instanceof String s ? DeliveryStatus.valueOf(s) : null;
        }
        this.status = target;
        this.updatedAt = Instant.now();
        AggregateEvent event = record(row, before.name(), old, trigger, actor, details);
        if (parcel != null) {
            Optional<ParcelEffect> effect = DeliveryParcelCorrespondence.effectOf(new DeliveryParcelCorrespondence
                    .Context(kind, row, operational, parcel.operationalStatus(), failureReason));
            effect.ifPresent(e -> parcel.applyDeliveryEffect(e, event.eventId(), actor, custodyAfter(row, before),
                    tenantId));
            if (effect.isEmpty() && kind == DeliveryKind.RETURN && D3.equals(row)) {
                // D-148: return cancelled before pickup -> the parcel stays RETURN_REQUESTED, with its own event
                parcel.returnDeliveryCancelledBeforePickup(event.eventId(), actor, tenantId);
            }
            if (target.isTerminal()) {
                parcel.releaseDelivery(id);
            }
        }
        return event;
    }

    private ParcelCustody custodyAfter(String row, DeliveryStatus before) {
        return switch (row) {
            case D4, D6 -> ParcelCustody.courier(deliveryPersonId);
            case D7 -> ParcelCustody.relay(relayHubId, hubPackageEntryId);
            case D8 -> before == DeliveryStatus.AT_RELAY_HUB ? ParcelCustody.courier(deliveryPersonId) : null;
            case D11 -> kind == DeliveryKind.RETURN ? ParcelCustody.sender(null) : ParcelCustody.recipient(null);
            default -> null;
        };
    }

    private AggregateEvent record(String row, String previousStatus, Map<String, Object> old, String trigger,
                                  TntEventActor actor, Map<String, Object> details) {
        AggregateEvent event = new AggregateEvent(TntAggregateType.DELIVERY, UUID.randomUUID(), id, tenantId,
                Instant.now(), AggregateEvent.nextVersion(version), TntAggregateType.DELIVERY.eventTypeFor(status.name()),
                row, previousStatus, old, trigger, actor, null, details, snapshot());
        domainEvents.add(event);
        this.nextActor = null;
        return event;
    }

    private void recordChange(String changeKind, Map<String, Object> old, String trigger, TntEventActor actor,
                              Map<String, Object> details) {
        domainEvents.add(new AggregateEvent(TntAggregateType.DELIVERY, UUID.randomUUID(), id, tenantId,
                Instant.now(), AggregateEvent.nextVersion(version), TntAggregateType.DELIVERY.eventTypeFor(changeKind),
                null, status.name(), old, trigger, takeActor(actor), null, details, snapshot()));
    }

    private TntEventActor takeActor(TntEventActor fallback) {
        TntEventActor actor = nextActor != null ? nextActor : fallback;
        this.nextActor = null;
        return actor;
    }

    private TntEventActor courier() {
        return nextActor != null ? nextActor : TntEventActor.of(TntEventActor.Type.COURIER, deliveryPersonId);
    }

    private TntEventActor sender() {
        return nextActor != null ? nextActor : TntEventActor.of(TntEventActor.Type.SENDER, senderId);
    }

    private void clearStorage() {
        this.relayDepositedAt = null;
        this.requestedStorageDays = null;
        this.relayStorageDays = null;
        this.relayStorageExpiresAt = null;
        this.storageReminderAt = null;
        this.storageReminderSentAt = null;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

}
