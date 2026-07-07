package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementPublishedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementResponseSelectedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Aggregate representing a client's delivery announcement.
 *
 * <p>A client (sender) publishes an announcement describing the parcel and locations.
 * Eligible delivery persons respond with an arrival time and optional note.
 * The client selects one response, which triggers creation of a {@link Delivery}.
 *
 * <p>Lifecycle:
 * DRAFT → PUBLISHED → IN_NEGOTIATION → ASSIGNED → COMPLETED | CANCELLED
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class DeliveryAnnouncement {

    private final UUID id;
    private final UUID tenantId;
    private final UUID clientId;

    private String title;
    private String description;
    private BigDecimal offeredAmount;
    private String currency;

    private final Parcel parcel;
    private final DeliveryAddress pickupAddress;
    private final DeliveryAddress deliveryAddress;
    private final RecipientInfo recipient;

    private final DeliveryUrgency urgency;

    private AnnouncementStatus status;

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
     * Factory method: creates a draft announcement.
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
        return DeliveryAnnouncement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .clientId(clientId)
                .title(title)
                .description(description)
                .offeredAmount(offeredAmount)
                .currency(currency)
                .parcel(parcel)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .recipient(recipient)
                .urgency(urgency)
                .status(AnnouncementStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Publishes the announcement, making it visible to eligible delivery persons.
     * Emits an {@link AnnouncementPublishedEvent} so the notification system can
     * broadcast to nearby drivers.
     */
    public void publish() {
        if (status != AnnouncementStatus.DRAFT) {
            throw new DeliveryDomainException(
                "Only DRAFT announcements can be published, current status: " + status);
        }
        validateForPublication();
        this.status = AnnouncementStatus.PUBLISHED;
        this.updatedAt = Instant.now();

        domainEvents.add(new AnnouncementPublishedEvent(id, tenantId, clientId,
                pickupAddress, deliveryAddress, urgency, offeredAmount, currency, updatedAt));
    }

    /**
     * Records a delivery person's response.
     * Transitions to IN_NEGOTIATION if this is the first response.
     */
    public void addResponse(AnnouncementResponse response) {
        if (status != AnnouncementStatus.PUBLISHED && status != AnnouncementStatus.IN_NEGOTIATION) {
            throw new DeliveryDomainException(
                "Cannot add response to announcement in status: " + status);
        }
        responses.add(response);
        if (status == AnnouncementStatus.PUBLISHED) {
            this.status = AnnouncementStatus.IN_NEGOTIATION;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Client selects a delivery person's response.
     * Rejects all other pending responses and transitions to ASSIGNED.
     *
     * @param responseId the chosen response id
     * @return the selected response
     */
    public AnnouncementResponse selectResponse(UUID responseId) {
        if (status != AnnouncementStatus.PUBLISHED && status != AnnouncementStatus.IN_NEGOTIATION) {
            throw new DeliveryDomainException(
                "Cannot select response for announcement in status: " + status);
        }
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

        this.selectedResponseId = responseId;
        this.status = AnnouncementStatus.ASSIGNED;
        this.updatedAt = Instant.now();

        domainEvents.add(new AnnouncementResponseSelectedEvent(
                id, tenantId, clientId, selected.getDeliveryPersonId(), updatedAt));
        return selected;
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
        if (status != AnnouncementStatus.ASSIGNED) {
            throw new DeliveryDomainException(
                "Cannot complete announcement in status: " + status);
        }
        this.status = AnnouncementStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /**
     * Cancels the announcement if not yet assigned.
     */
    public void cancel() {
        if (status == AnnouncementStatus.ASSIGNED || status == AnnouncementStatus.COMPLETED) {
            throw new DeliveryDomainException(
                "Cannot cancel announcement in status: " + status);
        }
        this.status = AnnouncementStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public List<DeliveryDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

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
        if (offeredAmount == null || offeredAmount.signum() <= 0) {
            throw new DeliveryDomainException("Offered amount must be positive");
        }
    }
}
