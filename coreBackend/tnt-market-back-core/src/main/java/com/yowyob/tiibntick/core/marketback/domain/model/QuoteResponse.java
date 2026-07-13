package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity — QuoteResponse.
 *
 * <p>A provider submits a QuoteResponse in reply to a client's QuoteRequest.
 * It is part of the QuoteRequest aggregate.</p>
 *
 * @author MANFOUO Braun
 */
public class QuoteResponse {

    private final QuoteResponseId id;
    private final UUID providerId;
    private Money proposedPrice;
    private LocalDateTime estimatedPickupAt;
    private LocalDateTime estimatedDeliveryAt;
    private double etaHours;
    private String message;
    private List<String> conditions;
    private LocalDateTime validUntil;
    private QuoteResponseStatus status;
    private final LocalDateTime createdAt;

    public static QuoteResponse create(
            UUID providerId,
            Money proposedPrice,
            LocalDateTime estimatedPickupAt,
            LocalDateTime estimatedDeliveryAt,
            double etaHours,
            String message,
            List<String> conditions,
            int validHours) {
        return new QuoteResponse(providerId, proposedPrice, estimatedPickupAt,
                estimatedDeliveryAt, etaHours, message, conditions, validHours);
    }

    private QuoteResponse(
            UUID providerId, Money proposedPrice,
            LocalDateTime estimatedPickupAt, LocalDateTime estimatedDeliveryAt,
            double etaHours, String message, List<String> conditions, int validHours) {
        this.id = QuoteResponseId.generate();
        this.providerId = providerId;
        this.proposedPrice = proposedPrice;
        this.estimatedPickupAt = estimatedPickupAt;
        this.estimatedDeliveryAt = estimatedDeliveryAt;
        this.etaHours = etaHours;
        this.message = message;
        this.conditions = conditions;
        this.validUntil = LocalDateTime.now().plusHours(validHours);
        this.status = QuoteResponseStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    QuoteResponse() {
        this.id = null; this.providerId = null; this.createdAt = null;
    }

    // -------------------------------------------------------
    // Business operations
    // -------------------------------------------------------

    public void accept() {
        this.status = QuoteResponseStatus.ACCEPTED;
    }

    public void reject() {
        this.status = QuoteResponseStatus.REJECTED;
    }

    public void withdraw() {
        if (status == QuoteResponseStatus.ACCEPTED) {
            throw new MarketDomainException("Cannot withdraw an already accepted response.");
        }
        this.status = QuoteResponseStatus.WITHDRAWN;
    }

    public boolean isValid() {
        return status == QuoteResponseStatus.PENDING
                && (validUntil == null || LocalDateTime.now().isBefore(validUntil));
    }

    // Getters
    public QuoteResponseId getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public Money getProposedPrice() { return proposedPrice; }
    public LocalDateTime getEstimatedPickupAt() { return estimatedPickupAt; }
    public LocalDateTime getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
    public double getEtaHours() { return etaHours; }
    public String getMessage() { return message; }
    public List<String> getConditions() { return conditions; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public QuoteResponseStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
