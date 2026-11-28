package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.delivery.domain.event.EventSource;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementCreatorRole;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementOutcome;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.AnnouncementStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Aggregate representing a client's delivery announcement.
 *
 * <p>A client (sender) publishes an announcement describing the parcel and locations.
 * Eligible delivery persons respond with an arrival time and optional note / proposed price.
 * The client selects one response, which triggers creation of a {@link Delivery}.
 *
 * <p>Lifecycle:
 * DRAFT → PUBLISHED → IN_NEGOTIATION → ASSIGNED → COMPLETED | CANCELLED
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class DeliveryAnnouncement implements EventSource {

    private final UUID id;
    private final UUID tenantId;
    private final UUID clientId;

    private String title;
    private String description;
    private BigDecimal offeredAmount;
    private String currency;

    @Builder.Default
    private AnnouncementPricingMode pricingMode = AnnouncementPricingMode.FIXED_PRICE;

    private final Parcel parcel;
    private final DeliveryAddress pickupAddress;
    private final DeliveryAddress deliveryAddress;
    private final RecipientInfo recipient;

    private final DeliveryUrgency urgency;

    /** Optional sender contact (null when not specified). */
    private SenderInfo sender;

    /** Optional generic complements (never null). */
    @Builder.Default
    private AnnouncementDetails details = AnnouncementDetails.EMPTY;

    private AnnouncementStatus status;

    /** OUTBOUND, or RETURN for the automatic return draft (QO-4). */
    @Builder.Default
    private AnnouncementKind kind = AnnouncementKind.OUTBOUND;
    /** CLIENT, or RELAY for a return of a parcel not collected at a relay (QO-4) [F]. */
    @Builder.Default
    private AnnouncementCreatorRole creatorRole = AnnouncementCreatorRole.CLIENT;
    /** How the delivery ended, set by A7 (QO-9). */
    private AnnouncementOutcome outcome;
    private Instant publishedAt;
    private Instant assignedAt;
    private Instant closedAt;

    @Builder.Default
    private final List<AnnouncementResponse> responses = new ArrayList<>();

    private UUID selectedResponseId;
    private UUID createdDeliveryId;

    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    // Domain events
    @Builder.Default
    private final List<DeliveryDomainEvent> domainEvents = new ArrayList<>();

    /**
     * Factory method: creates a draft announcement (defaults to {@link AnnouncementPricingMode#FIXED_PRICE}).
     */
    public static DeliveryAnnouncement createDraft(UUID tenantId,
                                                    UUID clientId,
                                                    String title,
                                                    String description,
                                                    BigDecimal offeredAmount,
                                                    String currency,
                                                    Parcel parcel,
                                                    DeliveryAddress pickupAddress,
                                                    DeliveryAddress deliveryAddress,
                                                    RecipientInfo recipient,
                                                    DeliveryUrgency urgency) {
        return createDraft(tenantId, clientId, title, description, offeredAmount, currency,
                AnnouncementPricingMode.FIXED_PRICE, parcel, pickupAddress, deliveryAddress,
                recipient, urgency);
    }

    /**
     * Factory method: creates a draft announcement with an explicit pricing mode.
     */
    public static DeliveryAnnouncement createDraft(UUID tenantId,
                                                    UUID clientId,
                                                    String title,
                                                    String description,
                                                    BigDecimal offeredAmount,
                                                    String currency,
                                                    AnnouncementPricingMode pricingMode,
                                                    Parcel parcel,
                                                    DeliveryAddress pickupAddress,
                                                    DeliveryAddress deliveryAddress,
                                                    RecipientInfo recipient,
                                                    DeliveryUrgency urgency) {
        return createDraft(tenantId, clientId, title, description, offeredAmount, currency, pricingMode,
                parcel, pickupAddress, deliveryAddress, recipient, urgency, null, null);
    }

    /**
     * Factory method: creates a draft announcement with an explicit pricing mode, an optional sender
     * and optional generic details.
     */
    public static DeliveryAnnouncement createDraft(UUID tenantId,
                                                    UUID clientId,
                                                    String title,
                                                    String description,
                                                    BigDecimal offeredAmount,
                                                    String currency,
                                                    AnnouncementPricingMode pricingMode,
                                                    Parcel parcel,
                                                    DeliveryAddress pickupAddress,
                                                    DeliveryAddress deliveryAddress,
                                                    RecipientInfo recipient,
                                                    DeliveryUrgency urgency,
                                                    SenderInfo sender,
                                                    AnnouncementDetails details) {
        AnnouncementPricingMode mode = pricingMode != null
                ? pricingMode : AnnouncementPricingMode.FIXED_PRICE;
        DeliveryAnnouncement announcement = DeliveryAnnouncement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .clientId(clientId)
                .title(title)
                .description(description)
                .offeredAmount(offeredAmount)
                .currency(currency)
                .pricingMode(mode)
                .parcel(parcel)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .recipient(recipient)
                .urgency(urgency)
                .sender(sender != null && !sender.isEmpty() ? sender : null)
                .details(details != null ? details : AnnouncementDetails.EMPTY)
                .status(AnnouncementStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        announcement.record(AnnouncementStateMachine.A1, null, Map.of(), "CREATE_DRAFT",
                TntEventActor.of(TntEventActor.Type.SENDER, clientId), Map.of());
        return announcement;
    }

    /**
     * Creates the RETURN announcement draft of a parcel that must go back to its sender (QO-4): created for the payer
     * of the return (the sender; the relay for a parcel not collected at a relay), never published automatically.
     */
    public static DeliveryAnnouncement createReturnDraft(UUID tenantId, UUID creatorId,
                                                         AnnouncementCreatorRole creatorRole, Parcel parcel,
                                                         DeliveryAddress pickupAddress,
                                                         DeliveryAddress deliveryAddress, RecipientInfo recipient,
                                                         String currency, UUID parentDeliveryId) {
        DeliveryAnnouncement announcement = DeliveryAnnouncement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .clientId(creatorId)
                .title("Return of parcel " + (parcel != null ? parcel.getTrackingCode() : ""))
                .description("Automatic return draft (delivery " + parentDeliveryId + ")")
                .currency(currency)
                .pricingMode(AnnouncementPricingMode.QUOTE_REQUEST)
                .parcel(parcel)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .recipient(recipient)
                .urgency(DeliveryUrgency.STANDARD)
                .details(new AnnouncementDetails(null, null, null, null, null, null, null, null,
                        "RETURN_OF_DELIVERY", parentDeliveryId))
                .kind(AnnouncementKind.RETURN)
                .creatorRole(creatorRole)
                .status(AnnouncementStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        announcement.record(AnnouncementStateMachine.A1, null, Map.of(), "RETURN_REQUESTED",
                TntEventActor.system(), Map.of());
        return announcement;
    }

    /**
     * Publishes the announcement, making it visible to eligible delivery persons.
     * Emits an {@link AnnouncementPublishedEvent} so the notification system can
     * broadcast to nearby drivers.
     */
    public void publish() {
        guard(AnnouncementStateMachine.A2, AnnouncementStatus.PUBLISHED);
        validateForPublication();
        this.publishedAt = Instant.now();
        transition(AnnouncementStateMachine.A2, AnnouncementStatus.PUBLISHED, Map.of(),
                "PUBLISH", creator(), Map.of());
    }

    public void addResponse(AnnouncementResponse response) {
        if (!status.isActive()) {
            throw new InvalidStateTransitionException("announcement", status, AnnouncementStatus.IN_NEGOTIATION,
                    "responses are accepted only while PUBLISHED or IN_NEGOTIATION");
        }
        validateResponsePricing(response);
        responses.add(response);
        TntEventActor courier = TntEventActor.of(TntEventActor.Type.COURIER, response.getDeliveryPersonId());
        Map<String, Object> details = Map.of("responseId", response.getId().toString());
        if (status == AnnouncementStatus.PUBLISHED) {
            transition(AnnouncementStateMachine.A3, AnnouncementStatus.IN_NEGOTIATION, Map.of(),
                    "RESPONSE_RECEIVED", courier, details);
        } else {
            this.updatedAt = Instant.now();
            recordChange("RESPONSE_RECEIVED", "RESPOND", courier, details);
        }
    }

    /** {@code AnnouncementResponseWithdrawn}: the courier withdraws its response (R4). */
    public AnnouncementResponse withdrawResponse(UUID responseId) {
        AnnouncementResponse response = responses.stream().filter(r -> r.getId().equals(responseId)).findFirst()
                .orElseThrow(() -> new DeliveryDomainException("Response not found: " + responseId));
        response.cancel();
        this.updatedAt = Instant.now();
        recordChange("RESPONSE_WITHDRAWN", "RESPONSE_WITHDRAWN",
                TntEventActor.of(TntEventActor.Type.COURIER, response.getDeliveryPersonId()),
                Map.of("responseId", responseId.toString()));
        return response;
    }

    /**
     * Client selects a delivery person's response.
     * Rejects all other pending responses and transitions to ASSIGNED.
     *
     * @param responseId the chosen response id
     * @return the selected response
     */
    public AnnouncementResponse selectResponse(UUID responseId) {
        guard(AnnouncementStateMachine.A4, AnnouncementStatus.ASSIGNED);
        AnnouncementResponse selected = responses.stream()
                .filter(r -> r.getId().equals(responseId))
                .findFirst()
                .orElseThrow(() -> new DeliveryDomainException(
                    "Response not found: " + responseId + " on announcement: " + id));

        selected.accept();

        // Reject all other pending responses
        responses.stream()
                .filter(r -> !r.getId().equals(responseId)
                        && r.getStatus() == ResponseStatus.SENT)
                .forEach(AnnouncementResponse::reject);

        Map<String, Object> previous = new java.util.LinkedHashMap<>();
        previous.put("selectedResponseId", null);
        this.selectedResponseId = responseId;
        this.assignedAt = Instant.now();
        transition(AnnouncementStateMachine.A4, AnnouncementStatus.ASSIGNED, previous, "SELECT_RESPONSE", creator(),
                Map.of("responseId", responseId.toString(),
                        "courierId", selected.getDeliveryPersonId().toString()));

        return selected;
    }

    /**
     * Effective escrow amount: offeredAmount for FIXED_PRICE, selected proposedPrice for QUOTE.
     */
    public BigDecimal resolveEscrowAmount(AnnouncementResponse selected) {
        if (pricingMode == AnnouncementPricingMode.QUOTE_REQUEST) {
            if (selected.getProposedPrice() == null || selected.getProposedPrice().signum() <= 0) {
                throw new DeliveryDomainException(
                        "Selected response has no valid proposedPrice for QUOTE_REQUEST announcement");
            }
            return selected.getProposedPrice();
        }
        if (offeredAmount == null || offeredAmount.signum() <= 0) {
            throw new DeliveryDomainException("Offered amount must be positive for FIXED_PRICE escrow");
        }
        return offeredAmount;
    }

    /**
     * Links the announcement to the created delivery.
     */
    public void linkDelivery(UUID deliveryId) {
        if (this.createdDeliveryId != null) {
            throw new DeliveryDomainException("Delivery already linked to announcement: " + id);
        }
        this.createdDeliveryId = deliveryId;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the announcement as completed after successful delivery.
     */
    public void complete() {
        complete(AnnouncementOutcome.DELIVERED);
    }

    /** Row A7: the delivery ended, delivered or finally failed (QO-9). */
    public void complete(AnnouncementOutcome deliveryOutcome) {
        java.util.Objects.requireNonNull(deliveryOutcome, "deliveryOutcome");
        guard(AnnouncementStateMachine.A7, AnnouncementStatus.COMPLETED);
        this.outcome = deliveryOutcome;
        this.closedAt = Instant.now();
        transition(AnnouncementStateMachine.A7, AnnouncementStatus.COMPLETED, Map.of(), "DELIVERY_ENDED",
                TntEventActor.system(), Map.of("outcome", deliveryOutcome.name()));
    }

    /**
     * Changes the offered amount and/or the currency (null = unchanged). Only while the announcement
     * is still open to responses.
     */
    public void updateTerms(BigDecimal newOfferedAmount, String newCurrency) {
        if (newOfferedAmount == null && newCurrency == null) {
            return;
        }
        if (status != AnnouncementStatus.DRAFT && status != AnnouncementStatus.PUBLISHED
                && status != AnnouncementStatus.IN_NEGOTIATION) {
            throw new DeliveryDomainException(
                "Cannot change the terms of an announcement in status: " + status);
        }
        if (newOfferedAmount != null) {
            if (newOfferedAmount.signum() <= 0) {
                throw new DeliveryDomainException("Offered amount must be positive");
            }
            this.offeredAmount = newOfferedAmount;
        }
        if (newCurrency != null && !newCurrency.isBlank()) {
            this.currency = newCurrency;
        }
        this.updatedAt = Instant.now();
        recordUpdated();
    }

    /**
     * Replaces the sender (when non-null) and merges the non-null components of {@code patch} into
     * the details. Not allowed once the announcement is completed or cancelled.
     */
    public void updateDetails(SenderInfo newSender, AnnouncementDetails patch) {
        if (status == AnnouncementStatus.COMPLETED || status == AnnouncementStatus.CANCELLED) {
            throw new DeliveryDomainException(
                "Cannot update an announcement in status: " + status);
        }
        if (newSender != null && !newSender.isEmpty()) {
            this.sender = newSender;
        }
        this.details = (details != null ? details : AnnouncementDetails.EMPTY).merge(patch);
        this.updatedAt = Instant.now();
        recordUpdated();
    }

    /**
     * Cancels the announcement if not yet assigned.
     */
    public void cancel() {
        if (status == AnnouncementStatus.ASSIGNED) {
            throw new InvalidStateTransitionException("announcement", status, AnnouncementStatus.CANCELLED,
                    "an assigned announcement is cancelled through its delivery (A6)");
        }
        guard(AnnouncementStateMachine.A5, AnnouncementStatus.CANCELLED);
        this.closedAt = Instant.now();
        transition(AnnouncementStateMachine.A5, AnnouncementStatus.CANCELLED, Map.of(), "CANCEL_ANNOUNCEMENT",
                creator(), Map.of());
    }

    /** Row A6: the delivery of the assigned announcement was cancelled. */
    public void cancelWithDelivery() {
        guard(AnnouncementStateMachine.A6, AnnouncementStatus.CANCELLED);
        this.closedAt = Instant.now();
        transition(AnnouncementStateMachine.A6, AnnouncementStatus.CANCELLED, Map.of(), "DELIVERY_CANCELLED",
                TntEventActor.system(), Map.of());
    }

    /** {@code AnnouncementUpdated}: terms or details changed without transition. */
    private void recordUpdated() {
        recordChange("UPDATED", "UPDATE_ANNOUNCEMENT", creator(), Map.of());
    }

    /** Called by the persistence adapter once saved, with the version written. */
    public void markPersisted(Long persistedVersion) {
        this.version = persistedVersion;
    }

    /** @return the unmasked snapshot of the announcement (spec §3.6) */
    public AnnouncementSnapshot snapshot() {
        return new AnnouncementSnapshot(id, kind, clientId, creatorRole, parcel != null ? parcel.getId() : null,
                status, pricingMode, offeredAmount, currency, pickupAddress, deliveryAddress,
                details != null ? details.destinationHubId() : null,
                responses.stream().map(r -> new AnnouncementSnapshot.Response(r.getId(), r.getDeliveryPersonId(),
                        r.getProposedPrice(), r.getStatus(), r.getCreatedAt())).toList(),
                selectedResponseId, createdDeliveryId, outcome, publishedAt, assignedAt, closedAt);
    }

    private void guard(String row, AnnouncementStatus target) {
        if (!AnnouncementStateMachine.TABLE.rowsFor(status, target).stream().anyMatch(r -> r.id().equals(row))) {
            throw new InvalidStateTransitionException("announcement", status, target, "row " + row);
        }
    }

    private void transition(String row, AnnouncementStatus target, Map<String, Object> old, String trigger,
                            TntEventActor actor, Map<String, Object> details) {
        AnnouncementStatus before = status;
        guard(row, target);
        this.status = target;
        this.updatedAt = Instant.now();
        record(row, before.name(), old, trigger, actor, details);
    }

    private void record(String row, String previousStatus, Map<String, Object> old, String trigger,
                        TntEventActor actor, Map<String, Object> details) {
        domainEvents.add(new AggregateEvent(TntAggregateType.ANNOUNCEMENT, UUID.randomUUID(), id, tenantId,
                Instant.now(), AggregateEvent.nextVersion(version),
                TntAggregateType.ANNOUNCEMENT.eventTypeFor(status.name()), row, previousStatus, old, trigger,
                actor, null, details, snapshot()));
    }

    private void recordChange(String changeKind, String trigger, TntEventActor actor, Map<String, Object> details) {
        domainEvents.add(new AggregateEvent(TntAggregateType.ANNOUNCEMENT, UUID.randomUUID(), id, tenantId,
                Instant.now(), AggregateEvent.nextVersion(version),
                TntAggregateType.ANNOUNCEMENT.eventTypeFor(changeKind), null, status.name(), Map.of(), trigger,
                actor, null, details, snapshot()));
    }

    private TntEventActor creator() {
        return TntEventActor.of(creatorRole == AnnouncementCreatorRole.RELAY ? TntEventActor.Type.RELAY_OP
                : TntEventActor.Type.SENDER, clientId);
    }

    @Override
    public List<DeliveryDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void validateForPublication() {
        if (title == null || title.isBlank()) {
            throw new DeliveryDomainException("Announcement title is required");
        }
        if (parcel == null) {
            throw new DeliveryDomainException("Parcel details are required");
        }
        if (pickupAddress == null || deliveryAddress == null) {
            throw new DeliveryDomainException("Pickup and delivery addresses are required");
        }
        if (recipient == null) {
            throw new DeliveryDomainException("Recipient information is required");
        }
        if (pricingMode == AnnouncementPricingMode.FIXED_PRICE) {
            if (offeredAmount == null || offeredAmount.signum() <= 0) {
                throw new DeliveryDomainException(
                        "Offered amount must be positive for FIXED_PRICE announcements");
            }
        }
        // QUOTE_REQUEST: offeredAmount is optional / nullable at publish
    }

    private void validateResponsePricing(AnnouncementResponse response) {
        if (pricingMode == AnnouncementPricingMode.QUOTE_REQUEST) {
            if (response.getProposedPrice() == null || response.getProposedPrice().signum() <= 0) {
                throw new DeliveryDomainException(
                        "proposedPrice is required when responding to a QUOTE_REQUEST announcement");
            }
        }
    }
}
