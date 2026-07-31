package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementPublishedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementResponseSelectedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
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
public class DeliveryAnnouncement {

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
        AnnouncementPricingMode mode = pricingMode != null
                ? pricingMode : AnnouncementPricingMode.FIXED_PRICE;
        return DeliveryAnnouncement.builder()
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
        validateResponsePricing(response);
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
