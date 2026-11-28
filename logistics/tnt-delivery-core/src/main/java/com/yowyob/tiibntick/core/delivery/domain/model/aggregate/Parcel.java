package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.EventSource;
import com.yowyob.tiibntick.core.delivery.domain.event.ParcelSnapshot;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DisputeParcelOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelReturnCause;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.TrackingCode;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence.ParcelEffect;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.TransitionTable;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine.*;

/**
 * The physical parcel and its state across all its deliveries (spec §1.2, decisions [B.1], [B.2], D-136).
 *
 * <p>Every state change goes through {@link ParcelStateMachine#TABLE} and records exactly one
 * {@code Parcel<PascalCase(target)>} event; custody moves without a state change record {@code ParcelCustodyChanged},
 * specification or photo changes {@code ParcelDetailsUpdated}. The parcel carries the tracking code (D-136): it is
 * assigned at creation, never changes, and follows the parcel through every delivery.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class Parcel implements EventSource {

    private final UUID id;
    /** D-136: public tracking code, assigned at creation, immutable, unique. */
    private String trackingCode;
    private PackageSpecification specification;
    private String photoUrl;

    @Builder.Default
    private ParcelStatus status = ParcelStatus.CREATED;
    private Instant statusChangedAt;
    private ParcelStatus previousStatusBeforeDispute;
    private UUID openDisputeId;
    /** QO-7: whether the open dispute freezes the delivery (any cause other than a delay). */
    private boolean disputeFreezesDelivery;
    /** The only non-terminal delivery of the parcel, {@code null} when none (rule "at most one"). */
    private UUID currentDeliveryId;
    @Builder.Default
    private List<UUID> deliveryIds = new ArrayList<>();
    @Builder.Default
    private ParcelCustody custody = ParcelCustody.none();
    private ParcelReturnCause returnCause;
    private ResponsibleParty returnCostPayer;
    private String closedReason;
    /** QO-8 / D-131: return deliveries that failed or were cancelled for this parcel. */
    private int failedReturnCount;

    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    /**
     * Tenant of the last operation, copied on the events (the parcel itself has no tenant). Never {@code null} on an
     * event: a parcel registered or changed without tenant belongs to the single system tenant (D-150).
     */
    private UUID eventTenantId;

    @Builder.Default
    private final List<DeliveryDomainEvent> domainEvents = new ArrayList<>();

    // ── Creation (P1) ─────────────────────────────────────────────────────────────────────────

    /** Registers a parcel without known sender (P1). */
    public static Parcel create(PackageSpecification specification) {
        return create(specification, null, null, TntEventActor.system());
    }

    /**
     * Registers a parcel (row P1): state {@code CREATED}, held by its sender, a new tracking code (D-136).
     *
     * @param specification physical characteristics, required
     * @param tenantId      tenant of the operation (copied on the event), {@code null} = system tenant (D-150)
     * @param senderId      sender holding the parcel, may be {@code null}
     * @param actor         who registers it
     */
    public static Parcel create(PackageSpecification specification, UUID tenantId, UUID senderId,
                                TntEventActor actor) {
        if (specification == null) {
            throw new DeliveryDomainException("PackageSpecification is required to create a Parcel");
        }
        Instant now = Instant.now();
        Parcel parcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingCode(TrackingCode.generate().value())
                .specification(specification)
                .status(ParcelStatus.CREATED)
                .statusChangedAt(now)
                .custody(ParcelCustody.sender(senderId))
                .eventTenantId(orSystemTenant(tenantId))
                .createdAt(now)
                .updatedAt(now)
                .build();
        parcel.record(P1, null, Map.of(), "PARCEL_REGISTERED", actor, null, Map.of());
        return parcel;
    }

    /**
     * Assigns a tracking code to a parcel registered before D-136 that has none (one-time backfill).
     *
     * @return whether a code was assigned
     */
    public boolean ensureTrackingCode() {
        if (trackingCode != null) {
            return false;
        }
        this.trackingCode = TrackingCode.generate().value();
        touch();
        return true;
    }

    // ── Details (no transition) ───────────────────────────────────────────────────────────────

    public void attachPhoto(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new DeliveryDomainException("Photo URL must not be blank");
        }
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("hasPhoto", this.photoUrl != null);
        this.photoUrl = photoUrl;
        touch();
        recordChange("DETAILS_UPDATED", old, "ATTACH_PHOTO", TntEventActor.system(), null, Map.of());
    }

    // ── Deliveries ────────────────────────────────────────────────────────────────────────────

    /**
     * Links a new delivery to the parcel (row D1 of the delivery; the parcel state does not change).
     *
     * @throws DeliveryDomainException when another non-terminal delivery exists, or the parcel is final
     */
    public void attachDelivery(UUID deliveryId) {
        Objects.requireNonNull(deliveryId, "deliveryId");
        if (status.isTerminal()) {
            throw new DeliveryDomainException("Parcel " + id + " is " + status + ": no new delivery");
        }
        if (currentDeliveryId != null && !currentDeliveryId.equals(deliveryId)) {
            throw new DeliveryDomainException("Parcel " + id + " already has a non-terminal delivery "
                    + currentDeliveryId + " (at most one at a time)");
        }
        this.currentDeliveryId = deliveryId;
        if (!deliveryIds.contains(deliveryId)) {
            deliveryIds.add(deliveryId);
        }
        touch();
    }

    /** Unlinks a delivery that reached a final state. */
    public void releaseDelivery(UUID deliveryId) {
        if (deliveryId != null && deliveryId.equals(currentDeliveryId)) {
            this.currentDeliveryId = null;
            touch();
        }
    }

    /**
     * Applies the parcel side of a delivery transition (spec §2.1), in the same operation as the delivery change.
     *
     * <p>While the parcel is {@code UNDER_DISPUTE} with a dispute that does not freeze the delivery (a delay, QO-7),
     * the delivery continues: the effect moves the memorised state (restored by P13) instead of the current one.
     *
     * @param effect      parcel row, target and cause from the correspondence
     * @param causationId event id of the delivery transition
     * @param actor       actor of the delivery transition
     * @param custodyAfter custody after the change, {@code null} to keep it
     * @param tenantId    tenant of the delivery
     */
    public void applyDeliveryEffect(ParcelEffect effect, UUID causationId, TntEventActor actor,
                                    ParcelCustody custodyAfter, UUID tenantId) {
        this.eventTenantId = tenantId;
        if (status == ParcelStatus.UNDER_DISPUTE) {
            TransitionTable.Row<ParcelStatus> row = TABLE.require(effect.parcelRow(), previousStatusBeforeDispute,
                    effect.target());
            Map<String, Object> old = new LinkedHashMap<>();
            old.put("previousStatusBeforeDispute", name(previousStatusBeforeDispute));
            this.previousStatusBeforeDispute = row.to().contains(effect.target()) ? effect.target()
                    : previousStatusBeforeDispute;
            applyReturnCause(effect, old);
            ParcelCustody before = custody;
            if (custodyAfter != null && !custodyAfter.equals(custody)) {
                old.put("custody", custody);
                this.custody = custodyAfter;
            }
            touch();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("from", before);
            details.put("to", custody);
            details.put("movement", "DELIVERY_UNDER_NON_FREEZING_DISPUTE");
            details.put("deliveryRow", effect.parcelRow());
            recordChange("CUSTODY_CHANGED", old, effect.trigger(), actor, causationId, details);
            return;
        }
        Map<String, Object> old = new LinkedHashMap<>();
        applyReturnCause(effect, old);
        if (effect.parcelRow().equals(P19)) {
            old.put("failedReturnCount", failedReturnCount);
            this.failedReturnCount++;
        }
        if (custodyAfter != null && !custodyAfter.equals(custody)) {
            old.put("custody", custody);
            this.custody = custodyAfter;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (effect.target() == ParcelStatus.RETURN_REQUESTED) {
            details.put("returnCause", name(returnCause));
            details.put("returnCostPayer", name(returnCostPayer));
        }
        transition(effect.parcelRow(), effect.target(), old, effect.trigger(), actor, causationId, details);
    }

    private void applyReturnCause(ParcelEffect effect, Map<String, Object> old) {
        if (effect.returnCause() != null) {
            old.put("returnCause", name(returnCause));
            old.put("returnCostPayer", name(returnCostPayer));
            this.returnCause = effect.returnCause();
            if (returnCostPayer == null) {
                this.returnCostPayer = ResponsibleParty.SENDER;
            }
        }
    }

    /**
     * D-148: the RETURN delivery was cancelled before its courier collected the parcel. The parcel stays
     * {@code RETURN_REQUESTED} (no transition) and records {@code ParcelReturnDeliveryCancelled}; the cancelled return
     * counts as a failed one (QO-8, D-131) and a new return draft is opened for the payer.
     */
    public void returnDeliveryCancelledBeforePickup(UUID causationId, TntEventActor actor, UUID tenantId) {
        if (status != ParcelStatus.RETURN_REQUESTED) {
            return;
        }
        this.eventTenantId = tenantId;
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("returnCause", name(returnCause));
        old.put("failedReturnCount", failedReturnCount);
        this.returnCause = ParcelReturnCause.RETURN_DELIVERY_CANCELLED;
        this.failedReturnCount++;
        touch();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("returnCause", returnCause.name());
        details.put("returnCostPayer", name(returnCostPayer));
        details.put("failedReturnCount", failedReturnCount);
        recordChange("RETURN_DELIVERY_CANCELLED", old, "RETURN_DELIVERY_CANCELLED", actor, causationId, details);
    }

    /** Sets who pays the return (fault-based, [B.6]); recorded with the next parcel event. */
    public void setReturnCostPayer(ResponsibleParty payer) {
        this.returnCostPayer = payer;
        touch();
    }

    /**
     * Records that a courier now holds the parcel in place of another (hand-over D18, decision [B.2]).
     */
    public void changeCustody(ParcelCustody newCustody, String trigger, String movement, TntEventActor actor,
                              UUID causationId, UUID tenantId) {
        Objects.requireNonNull(newCustody, "newCustody");
        if (newCustody.equals(custody)) {
            return;
        }
        this.eventTenantId = tenantId;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", custody);
        details.put("to", newCustody);
        details.put("movement", movement);
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("custody", custody);
        this.custody = newCustody;
        touch();
        recordChange("CUSTODY_CHANGED", old, trigger, actor, causationId, details);
    }

    // ── Dispute (P12–P17) ─────────────────────────────────────────────────────────────────────

    /**
     * Opens a dispute on the parcel (row P12).
     *
     * @param disputeId        dispute opened
     * @param freezesDelivery  whether the dispute freezes the delivery (QO-7: any cause other than a delay)
     * @param withinFilingWindow for a closed parcel (DELIVERED, RETURNED): whether the filing window is still open
     */
    public void openDispute(UUID disputeId, boolean freezesDelivery, boolean withinFilingWindow,
                            TntEventActor actor, UUID causationId, UUID tenantId) {
        Objects.requireNonNull(disputeId, "disputeId");
        if (openDisputeId != null) {
            throw new InvalidStateTransitionException("parcel", status, ParcelStatus.UNDER_DISPUTE,
                    "dispute " + openDisputeId + " is already open");
        }
        if (status.isClosed() && !withinFilingWindow) {
            throw new InvalidStateTransitionException("parcel", status, ParcelStatus.UNDER_DISPUTE,
                    "the dispute filing window is over");
        }
        this.eventTenantId = tenantId;
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("previousStatusBeforeDispute", null);
        old.put("openDisputeId", null);
        ParcelStatus before = status;
        requireRow(P12, before, ParcelStatus.UNDER_DISPUTE);
        this.previousStatusBeforeDispute = before;
        this.openDisputeId = disputeId;
        this.disputeFreezesDelivery = freezesDelivery;
        transition(P12, ParcelStatus.UNDER_DISPUTE, old, "DISPUTE_OPENED", actor, causationId,
                Map.of("disputeId", disputeId.toString()));
    }

    /**
     * Applies the parcel part of a dispute outcome (rows P13–P17, decision [B.7]).
     *
     * @param outcome            effect on the parcel
     * @param trigger            {@code DISPUTE_<issue>}
     * @param compensationAmount amount compensated, for LOST/DAMAGED details, may be {@code null}
     */
    public void applyDisputeOutcome(DisputeParcelOutcome outcome, String trigger, String compensationAmount,
                                    TntEventActor actor, UUID causationId, UUID tenantId) {
        if (status != ParcelStatus.UNDER_DISPUTE) {
            throw new InvalidStateTransitionException("parcel", status, outcome, "no dispute is open");
        }
        this.eventTenantId = tenantId;
        UUID disputeId = openDisputeId;
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("previousStatusBeforeDispute", name(previousStatusBeforeDispute));
        old.put("openDisputeId", disputeId);
        Map<String, Object> details = new LinkedHashMap<>();
        String row;
        ParcelStatus target;
        switch (outcome) {
            case RESTORE_PREVIOUS -> {
                row = P13;
                target = previousStatusBeforeDispute;
            }
            case LOST -> {
                row = P14;
                target = ParcelStatus.LOST;
                details.put("disputeId", disputeId.toString());
                details.put("compensationAmount", compensationAmount);
                this.closedReason = "TOTAL_LOSS_COMPENSATED";
            }
            case DAMAGED -> {
                row = P15;
                target = ParcelStatus.DAMAGED;
                details.put("disputeId", disputeId.toString());
                details.put("compensationAmount", compensationAmount);
                this.closedReason = "DAMAGE_COMPENSATED";
            }
            case RETURN_TO_SENDER -> {
                row = P16;
                target = ParcelStatus.RETURN_REQUESTED;
                old.put("returnCause", name(returnCause));
                this.returnCause = ParcelReturnCause.DISPUTE_RETURN_TO_SENDER_ORDERED;
                if (returnCostPayer == null) {
                    this.returnCostPayer = ResponsibleParty.SENDER;
                }
                details.put("returnCause", returnCause.name());
                details.put("returnCostPayer", name(returnCostPayer));
            }
            case REDELIVERY -> {
                row = P17;
                target = ParcelStatus.CREATED;
            }
            default -> throw new IllegalStateException("Unknown outcome " + outcome);
        }
        requireRow(row, status, target);
        this.previousStatusBeforeDispute = null;
        this.openDisputeId = null;
        this.disputeFreezesDelivery = false;
        transition(row, target, old, trigger, actor, causationId, details);
    }

    /** @return whether a dispute currently freezes the delivery of this parcel (QO-7) */
    public boolean isFrozenByDispute() {
        return status == ParcelStatus.UNDER_DISPUTE && disputeFreezesDelivery;
    }

    /**
     * The state the delivery correspondence must reason on: the memorised state while under dispute, the current
     * state otherwise.
     */
    public ParcelStatus operationalStatus() {
        return status == ParcelStatus.UNDER_DISPUTE ? previousStatusBeforeDispute : status;
    }

    // ── Persistence / events ──────────────────────────────────────────────────────────────────

    /** Called by the persistence adapter once the parcel is saved, with the version written. */
    public void markPersisted(Long persistedVersion) {
        this.version = persistedVersion;
    }

    /** @return whether the parcel has unsaved recorded changes (new, or events pending) */
    public boolean hasPendingChanges() {
        return version == null || !domainEvents.isEmpty() || dirty;
    }

    @Builder.Default
    private boolean dirty = false;

    /** Marks the parcel saved: no pending change. */
    public void markClean() {
        this.dirty = false;
    }

    /** @return the unmasked snapshot of the parcel (spec §3.6) */
    public ParcelSnapshot snapshot() {
        return new ParcelSnapshot(id, trackingCode, status, previousStatusBeforeDispute, openDisputeId,
                currentDeliveryId, deliveryIds, custody, specification, photoUrl != null,
                name(returnCause), returnCostPayer, closedReason, failedReturnCount, createdAt, updatedAt,
                statusChangedAt);
    }

    @Override
    public List<DeliveryDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public List<UUID> getDeliveryIds() {
        return Collections.unmodifiableList(deliveryIds);
    }

    private void transition(String row, ParcelStatus target, Map<String, Object> old, String trigger,
                            TntEventActor actor, UUID causationId, Map<String, Object> details) {
        ParcelStatus before = status;
        requireRow(row, before, target);
        this.status = target;
        this.statusChangedAt = Instant.now();
        touch();
        record(row, before.name(), old, trigger, actor, causationId, details);
    }

    private void requireRow(String row, ParcelStatus from, ParcelStatus to) {
        try {
            TABLE.require(row, from, to);
        } catch (IllegalStateException e) {
            throw new InvalidStateTransitionException("parcel", from, to, "row " + row);
        }
    }

    private void record(String row, String previousStatus, Map<String, Object> old, String trigger,
                        TntEventActor actor, UUID causationId, Map<String, Object> details) {
        domainEvents.add(new AggregateEvent(TntAggregateType.PARCEL, UUID.randomUUID(), id, eventTenant(),
                Instant.now(), AggregateEvent.nextVersion(version), TntAggregateType.PARCEL.eventTypeFor(status.name()),
                row, previousStatus, old, trigger, actor == null ? TntEventActor.system() : actor, causationId,
                details, snapshot()));
    }

    private void recordChange(String changeKind, Map<String, Object> old, String trigger, TntEventActor actor,
                              UUID causationId, Map<String, Object> details) {
        domainEvents.add(new AggregateEvent(TntAggregateType.PARCEL, UUID.randomUUID(), id, eventTenant(),
                Instant.now(), AggregateEvent.nextVersion(version), TntAggregateType.PARCEL.eventTypeFor(changeKind),
                null, status.name(), old, trigger, actor == null ? TntEventActor.system() : actor, causationId,
                details, snapshot()));
    }

    /** D-150: the tenant copied on an event, the system tenant when the operation has none. */
    private UUID eventTenant() {
        this.eventTenantId = orSystemTenant(eventTenantId);
        return eventTenantId;
    }

    private static UUID orSystemTenant(UUID tenantId) {
        return tenantId != null ? tenantId : SystemTenant.id();
    }

    private void touch() {
        this.updatedAt = Instant.now();
        this.dirty = true;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
