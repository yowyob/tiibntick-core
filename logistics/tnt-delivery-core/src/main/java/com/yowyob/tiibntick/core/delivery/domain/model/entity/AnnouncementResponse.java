package com.yowyob.tiibntick.core.delivery.domain.model.entity;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a delivery person's response (bid) to a client's delivery announcement.
 * Entity owned by the {@code DeliveryAnnouncement} aggregate.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class AnnouncementResponse {

    private final UUID id;
    private final UUID announcementId;
    private final UUID deliveryPersonId;

    /** Delivery person's estimated arrival time at pickup address. */
    private final Instant estimatedArrivalTime;

    /** Optional note from the delivery person. */
    private String note;

    /** Proposed price when announcement is QUOTE_REQUEST (nullable for FIXED_PRICE). */
    private final BigDecimal proposedPrice;

    /** ISO currency for {@link #proposedPrice}; typically matches announcement currency. */
    private final String proposedCurrency;

    private ResponseStatus status;

    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    /**
     * Factory method for a new response (no proposed price — FIXED_PRICE path).
     */
    public static AnnouncementResponse create(UUID announcementId,
                                               UUID deliveryPersonId,
                                               Instant estimatedArrivalTime,
                                               String note) {
        return create(announcementId, deliveryPersonId, estimatedArrivalTime, note, null, null);
    }

    /**
     * Factory method for a new response with optional proposed price (QUOTE_REQUEST).
     */
    public static AnnouncementResponse create(UUID announcementId,
                                               UUID deliveryPersonId,
                                               Instant estimatedArrivalTime,
                                               String note,
                                               BigDecimal proposedPrice,
                                               String proposedCurrency) {
        return AnnouncementResponse.builder()
                .id(UUID.randomUUID())
                .announcementId(announcementId)
                .deliveryPersonId(deliveryPersonId)
                .estimatedArrivalTime(estimatedArrivalTime)
                .note(note)
                .proposedPrice(proposedPrice)
                .proposedCurrency(proposedCurrency)
                .status(ResponseStatus.SENT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Marks this response as the selected one.
     */
    public void accept() {
        this.status = ResponseStatus.ACCEPTED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks this response as rejected.
     */
    public void reject() {
        this.status = ResponseStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    /**
     * Delivery person withdraws their offer.
     */
    public void cancel() {
        if (this.status != ResponseStatus.SENT) {
            throw new DeliveryDomainException(
                "Only SENT responses can be cancelled, current: " + status);
        }
        this.status = ResponseStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
