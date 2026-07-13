package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.event.MarketOrderCompletedEvent;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketOrderCreatedEvent;
import com.yowyob.tiibntick.core.marketback.domain.event.MarketOrderPaidEvent;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — MarketOrder.
 *
 * <p>A MarketOrder is created when a client confirms a service request (direct or
 * from a quote) on TiiBnTick Market. It holds full lifecycle from placement
 * through payment, dispatch, delivery and completion.</p>
 *
 * @author MANFOUO Braun
 */
public class MarketOrder {

    private final MarketOrderId id;
    private final String tenantId;
    private final UUID clientId;
    private final UUID providerId;
    private final MarketListingId listingId;
    private final ServiceOfferId offerId;
    private final QuoteRequestId quoteRequestId;
    private final DeliveryRequest deliveryRequest;

    private OrderStatus status;
    private OrderPricing pricing;
    private PaymentInfo paymentInfo;
    private UUID missionId;
    private UUID invoiceId;
    private String cancellationReason;
    private LocalDateTime cancelledAt;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    // -------------------------------------------------------
    // Factory — direct order from offer
    // -------------------------------------------------------

    public static MarketOrder create(
            String tenantId,
            UUID clientId,
            UUID providerId,
            MarketListingId listingId,
            ServiceOfferId offerId,
            DeliveryRequest deliveryRequest,
            OrderPricing pricing) {
        return new MarketOrder(tenantId, clientId, providerId, listingId,
                offerId, null, deliveryRequest, pricing);
    }

    /** Factory — order created from an accepted quote. */
    public static MarketOrder fromQuote(
            QuoteRequest quote,
            QuoteResponse selectedResponse,
            String tenantId) {
        OrderPricing pricing = OrderPricing.fromEstimate(selectedResponse.getProposedPrice());
        return new MarketOrder(tenantId, quote.getClientId(), quote.getProviderId(),
                quote.getListingId(), null, quote.getId(), quote.getDeliveryRequest(), pricing);
    }

    private MarketOrder(
            String tenantId, UUID clientId, UUID providerId,
            MarketListingId listingId, ServiceOfferId offerId,
            QuoteRequestId quoteRequestId, DeliveryRequest deliveryRequest,
            OrderPricing pricing) {
        this.id = MarketOrderId.generate();
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.providerId = providerId;
        this.listingId = listingId;
        this.offerId = offerId;
        this.quoteRequestId = quoteRequestId;
        this.deliveryRequest = deliveryRequest;
        this.pricing = pricing;
        this.status = OrderStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        domainEvents.add(new MarketOrderCreatedEvent(id, clientId, providerId, tenantId,
                pricing.total(), createdAt));
    }

    MarketOrder() {
        this.id = null; this.tenantId = null; this.clientId = null;
        this.providerId = null; this.listingId = null; this.offerId = null;
        this.quoteRequestId = null; this.deliveryRequest = null; this.createdAt = null;
    }

    /** Reconstitutes a MarketOrder from persistence. */
    public static MarketOrder reconstitute(
            MarketOrderId id, String tenantId, UUID clientId, UUID providerId,
            MarketListingId listingId, ServiceOfferId offerId, QuoteRequestId quoteRequestId,
            OrderStatus status, DeliveryRequest deliveryRequest, OrderPricing pricing,
            PaymentInfo paymentInfo, String deliveryMissionId, UUID invoiceId,
            String cancellationReason, LocalDateTime cancelledAt,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime completedAt) {
        // Use a full-state private constructor
        return new MarketOrder(id, tenantId, clientId, providerId, listingId, offerId,
                quoteRequestId, status, deliveryRequest, pricing, paymentInfo,
                deliveryMissionId != null ? UUID.fromString(deliveryMissionId) : null,
                invoiceId, cancellationReason, cancelledAt, createdAt, updatedAt);
    }

    private MarketOrder(
            MarketOrderId id, String tenantId, UUID clientId, UUID providerId,
            MarketListingId listingId, ServiceOfferId offerId, QuoteRequestId quoteRequestId,
            OrderStatus status, DeliveryRequest deliveryRequest, OrderPricing pricing,
            PaymentInfo paymentInfo, UUID missionId, UUID invoiceId,
            String cancellationReason, LocalDateTime cancelledAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.providerId = providerId;
        this.listingId = listingId;
        this.offerId = offerId;
        this.quoteRequestId = quoteRequestId;
        this.status = status;
        this.deliveryRequest = deliveryRequest;
        this.pricing = pricing;
        this.paymentInfo = paymentInfo;
        this.missionId = missionId;
        this.invoiceId = invoiceId;
        this.cancellationReason = cancellationReason;
        this.cancelledAt = cancelledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------
    // Business operations
    // -------------------------------------------------------

    public void confirm() {
        if (status != OrderStatus.DRAFT) {
            throw new MarketDomainException("Order can only be confirmed from DRAFT status.");
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsPaid(PaymentInfo info) {
        if (status != OrderStatus.CONFIRMED) {
            throw new MarketDomainException("Order must be CONFIRMED before payment.");
        }
        this.paymentInfo = info;
        this.status = OrderStatus.PAID;
        this.updatedAt = LocalDateTime.now();
        domainEvents.add(new MarketOrderPaidEvent(
                id, info.transactionRef(), info.paymentMethod(), info.paidAmount(), updatedAt));
    }

    public void dispatch(UUID missionId) {
        if (status != OrderStatus.PAID) {
            throw new MarketDomainException("Order must be PAID before dispatching.");
        }
        this.missionId = missionId;
        this.status = OrderStatus.DISPATCHED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markInTransit() {
        if (status != OrderStatus.DISPATCHED) {
            throw new MarketDomainException("Order must be DISPATCHED to go IN_TRANSIT.");
        }
        this.status = OrderStatus.IN_TRANSIT;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDelivered() {
        if (status != OrderStatus.IN_TRANSIT) {
            throw new MarketDomainException("Order must be IN_TRANSIT to be DELIVERED.");
        }
        this.status = OrderStatus.DELIVERED;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (status != OrderStatus.DELIVERED) {
            throw new MarketDomainException("Order must be DELIVERED to be COMPLETED.");
        }
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
        domainEvents.add(new MarketOrderCompletedEvent(id, providerId, clientId, updatedAt));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.IN_TRANSIT || status == OrderStatus.DELIVERED
                || status == OrderStatus.COMPLETED) {
            throw new MarketDomainException("Cannot cancel order in status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDateTime.now();
        this.updatedAt = this.cancelledAt;
    }

    public void requestRefund() {
        if (status != OrderStatus.DELIVERED && status != OrderStatus.COMPLETED
                && status != OrderStatus.CANCELLED) {
            throw new MarketDomainException("Refund can only be requested after delivery or cancellation.");
        }
        this.status = OrderStatus.REFUND_REQUESTED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markRefunded() {
        this.status = OrderStatus.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void applyDiscount(DiscountRule discount) {
        Money discountedTotal = discount.apply(pricing.total());
        Money discountAmount = pricing.total().subtract(discountedTotal);
        this.pricing = new OrderPricing(
                pricing.baseAmount(), pricing.distanceFee(), pricing.weightFee(),
                pricing.insuranceFee(), pricing.expressFee(),
                discountAmount, discountedTotal, pricing.currency(), pricing.breakdown());
        this.updatedAt = LocalDateTime.now();
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
        this.updatedAt = LocalDateTime.now();
    }

    public List<Object> pullDomainEvents() {
        List<Object> evts = List.copyOf(domainEvents);
        domainEvents.clear();
        return evts;
    }

    // Getters
    public MarketOrderId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getClientId() { return clientId; }
    public UUID getProviderId() { return providerId; }
    public MarketListingId getListingId() { return listingId; }
    public ServiceOfferId getOfferId() { return offerId; }
    public QuoteRequestId getQuoteRequestId() { return quoteRequestId; }
    public DeliveryRequest getDeliveryRequest() { return deliveryRequest; }
    public OrderStatus getStatus() { return status; }
    public OrderPricing getPricing() { return pricing; }
    public PaymentInfo getPaymentInfo() { return paymentInfo; }
    public UUID getMissionId() { return missionId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getCancellationReason() { return cancellationReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
