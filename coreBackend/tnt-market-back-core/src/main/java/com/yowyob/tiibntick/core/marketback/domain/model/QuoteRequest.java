package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.event.QuoteRequestCreatedEvent;
import com.yowyob.tiibntick.core.marketback.domain.event.QuoteResponseSubmittedEvent;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — QuoteRequest (Demande de Devis).
 *
 * <p>A client submits a QuoteRequest targeting a specific provider listing.
 * The provider responds with one or more QuoteResponse objects. The client
 * selects one, which is then converted into a MarketOrder.</p>
 *
 * @author MANFOUO Braun
 */
public class QuoteRequest {

    private final QuoteRequestId id;
    private final String tenantId;
    private final UUID clientId;
    private final MarketListingId listingId;
    private final UUID providerId;

    private QuoteStatus status;
    private final DeliveryRequest deliveryRequest;
    private final List<QuoteResponse> responses = new ArrayList<>();
    private QuoteResponseId selectedResponseId;
    private LocalDateTime expiresAt;
    private String notes;
    private String cancellationReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    // -------------------------------------------------------
    // Factory
    // -------------------------------------------------------

    public static QuoteRequest create(
            String tenantId,
            UUID clientId,
            MarketListingId listingId,
            UUID providerId,
            DeliveryRequest deliveryRequest,
            int expiryHours) {
        return new QuoteRequest(tenantId, clientId, listingId, providerId, deliveryRequest, expiryHours);
    }

    private QuoteRequest(
            String tenantId,
            UUID clientId,
            MarketListingId listingId,
            UUID providerId,
            DeliveryRequest deliveryRequest,
            int expiryHours) {
        this.id = QuoteRequestId.generate();
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.listingId = listingId;
        this.providerId = providerId;
        this.deliveryRequest = deliveryRequest;
        this.status = QuoteStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.expiresAt = createdAt.plusHours(expiryHours);
        domainEvents.add(new QuoteRequestCreatedEvent(id, listingId, clientId, providerId, createdAt));
    }

    QuoteRequest() {
        this.id = null; this.tenantId = null; this.clientId = null;
        this.listingId = null; this.providerId = null; this.deliveryRequest = null; this.createdAt = null;
    }

    /** Reconstitutes a QuoteRequest aggregate from its persisted state. */
    public static QuoteRequest reconstitute(
            QuoteRequestId id, String tenantId, UUID clientId,
            MarketListingId listingId, UUID providerId,
            QuoteStatus status, DeliveryRequest deliveryRequest,
            List<QuoteResponse> responses,
            QuoteResponseId selectedResponseId,
            LocalDateTime expiresAt, String notes, String cancellationReason,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new QuoteRequest(id, tenantId, clientId, listingId, providerId,
                status, deliveryRequest, responses, selectedResponseId,
                expiresAt, notes, cancellationReason, createdAt, updatedAt);
    }

    private QuoteRequest(
            QuoteRequestId id, String tenantId, UUID clientId,
            MarketListingId listingId, UUID providerId,
            QuoteStatus status, DeliveryRequest deliveryRequest,
            List<QuoteResponse> responses,
            QuoteResponseId selectedResponseId,
            LocalDateTime expiresAt, String notes, String cancellationReason,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.listingId = listingId;
        this.providerId = providerId;
        this.status = status;
        this.deliveryRequest = deliveryRequest;
        if (responses != null) this.responses.addAll(responses);
        this.selectedResponseId = selectedResponseId;
        this.expiresAt = expiresAt;
        this.notes = notes;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------
    // Business operations
    // -------------------------------------------------------

    /** Adds a provider quote response to this request. */
    public void addResponse(QuoteResponse response) {
        if (status != QuoteStatus.PENDING && status != QuoteStatus.RESPONDED) {
            throw new MarketDomainException("Cannot add response to quote in status: " + status);
        }
        if (isExpired()) {
            throw new MarketDomainException("Quote request has expired.");
        }
        responses.add(response);
        this.status = QuoteStatus.RESPONDED;
        this.updatedAt = LocalDateTime.now();
        domainEvents.add(new QuoteResponseSubmittedEvent(
                id, response.getId(), response.getProviderId(),
                response.getProposedPrice(), LocalDateTime.now()));
    }

    /** Client selects a response. */
    public void selectResponse(QuoteResponseId responseId) {
        if (status != QuoteStatus.RESPONDED) {
            throw new MarketDomainException("No responses available to select from.");
        }
        responses.stream()
                .filter(r -> r.getId().equals(responseId))
                .findFirst()
                .orElseThrow(() -> new MarketDomainException("Response not found: " + responseId));
        this.selectedResponseId = responseId;
        this.status = QuoteStatus.SELECTED;
        this.updatedAt = LocalDateTime.now();
    }

    /** Marks the request as converted to a MarketOrder. */
    public void markConvertedToOrder() {
        if (status != QuoteStatus.SELECTED) {
            throw new MarketDomainException("Quote must be SELECTED before conversion.");
        }
        this.status = QuoteStatus.CONVERTED_TO_ORDER;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        if (status == QuoteStatus.CONVERTED_TO_ORDER) {
            throw new MarketDomainException("Cannot cancel a quote already converted to order.");
        }
        this.status = QuoteStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = QuoteStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasSelectedResponse() {
        return selectedResponseId != null;
    }

    public QuoteResponse getSelectedResponse() {
        if (selectedResponseId == null) return null;
        return responses.stream()
                .filter(r -> r.getId().equals(selectedResponseId))
                .findFirst().orElse(null);
    }

    public List<Object> pullDomainEvents() {
        List<Object> evts = List.copyOf(domainEvents);
        domainEvents.clear();
        return evts;
    }

    // Getters
    public QuoteRequestId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getClientId() { return clientId; }
    public MarketListingId getListingId() { return listingId; }
    public UUID getProviderId() { return providerId; }
    public QuoteStatus getStatus() { return status; }
    public DeliveryRequest getDeliveryRequest() { return deliveryRequest; }
    public List<QuoteResponse> getResponses() { return List.copyOf(responses); }
    public QuoteResponseId getSelectedResponseId() { return selectedResponseId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCancellationReason() { return cancellationReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
