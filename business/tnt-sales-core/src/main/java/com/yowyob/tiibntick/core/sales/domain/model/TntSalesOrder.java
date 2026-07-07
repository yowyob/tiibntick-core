package com.yowyob.tiibntick.core.sales.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * TiiBnTick SalesOrder aggregate root.
 *
 * <p>Extends the Kernel's SalesOrder concept (RT-comops-sales-core) with a richer
 * logistics lifecycle adapted to Cameroonian informal commerce:</p>
 * <pre>
 *   DRAFT → CONFIRMED → STOCK_RESERVED → DISPATCHED → IN_DELIVERY → DELIVERED
 *   Any non-DELIVERED → CANCELLED
 *   DISPATCHED | IN_DELIVERY → RETURNED
 * </pre>
 *
 * <p><b>Kernel integration:</b> {@code kernelSalesOrderId} is an optional logical reference
 * to the matching sales order in the Yowyob Kernel (RT-comops-sales-core). It is {@code null}
 * for informal, cash-on-delivery, or unregistered transactions. There is <em>no Java
 * inheritance</em> from Kernel order classes — the link is a UUID stored in
 * {@code sales.orders.kernel_sales_order_id}.</p>
 *
 * @author MANFOUO Braun
 */
public final class TntSalesOrder {

    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID agencyId;
    private final UUID clientThirdPartyId;
    private final String orderNumber;
    private final List<TntSalesOrderLine> lines;
    private final TntAddress deliveryAddress;
    private final TntAddress billingAddress;
    private final SalesOrderStatus status;
    private final OrderPriority priority;
    private final PaymentStatus paymentStatus;
    private final String currency;
    private final BigDecimal subtotalAmount;
    private final BigDecimal totalAmount;

    /** ID of the linked delivery mission (set after DISPATCHED). */
    private final UUID missionId;

    /** ID of the linked invoice (set after CONFIRMED). */
    private final UUID invoiceId;

    /**
     * Optional logical reference to the Kernel sales order (RT-comops-sales-core).
     * {@code null} for informal or unregistered transactions.
     * No physical FK cross-database — logical reference only.
     */
    private final UUID kernelSalesOrderId;

    // ── : FreelancerOrg provider context ─────────────────────────────────────

    /**
     * Type of the logistics service provider for this order.
     * Values: {@code "AGENCY"} (default) | {@code "FREELANCER_ORG"}.
     * Null for orders not yet dispatched or for agency-only tenants.
     */
    private final String providerOrgType;

    /**
     * UUID of the providing organization (FreelancerOrg or Agency).
     * For FreelancerOrg orders: the FreelancerOrg UUID from tnt-organization-core.
     * For Agency orders: the agencyId (redundant but explicit for reporting).
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private final String providerOrgId;

    private final ReturnReason returnReason;
    private final String returnNote;
    private final String cancelReason;
    private final Instant confirmedAt;
    private final Instant deliveredAt;
    private final Instant returnedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TntSalesOrder(UUID id, UUID tenantId, UUID organizationId, UUID agencyId,
                           UUID clientThirdPartyId, String orderNumber,
                           List<TntSalesOrderLine> lines, TntAddress deliveryAddress,
                           TntAddress billingAddress, SalesOrderStatus status,
                           OrderPriority priority, PaymentStatus paymentStatus,
                           String currency, BigDecimal subtotalAmount, BigDecimal totalAmount,
                           UUID missionId, UUID invoiceId, UUID kernelSalesOrderId,
                           String providerOrgType, String providerOrgId,
                           ReturnReason returnReason, String returnNote, String cancelReason,
                           Instant confirmedAt, Instant deliveredAt, Instant returnedAt,
                           Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.agencyId = Objects.requireNonNull(agencyId);
        this.clientThirdPartyId = Objects.requireNonNull(clientThirdPartyId);
        this.orderNumber = requireText(orderNumber, "orderNumber");
        this.lines = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(lines)));
        this.deliveryAddress = Objects.requireNonNull(deliveryAddress, "deliveryAddress is required");
        this.billingAddress = billingAddress;
        this.status = Objects.requireNonNull(status);
        this.priority = Objects.requireNonNull(priority);
        this.paymentStatus = Objects.requireNonNull(paymentStatus);
        this.currency = requireText(currency, "currency").toUpperCase();
        this.subtotalAmount = Objects.requireNonNull(subtotalAmount);
        this.totalAmount = Objects.requireNonNull(totalAmount);
        this.missionId = missionId;
        this.invoiceId = invoiceId;
        this.kernelSalesOrderId = kernelSalesOrderId; // nullable — optional Kernel link
        this.providerOrgType = providerOrgType;
        this.providerOrgId = providerOrgId;
        this.returnReason = returnReason;
        this.returnNote = returnNote;
        this.cancelReason = cancelReason;
        this.confirmedAt = confirmedAt;
        this.deliveredAt = deliveredAt;
        this.returnedAt = returnedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new SalesOrder in DRAFT status with no Kernel link.
     */
    public static TntSalesOrder create(UUID tenantId, UUID organizationId, UUID agencyId,
                                        UUID clientThirdPartyId, String orderNumber,
                                        List<TntSalesOrderLine> lines, TntAddress deliveryAddress,
                                        TntAddress billingAddress, OrderPriority priority,
                                        String currency) {
        return create(tenantId, organizationId, agencyId, clientThirdPartyId, orderNumber,
                lines, deliveryAddress, billingAddress, priority, currency, null);
    }

    /**
     * Creates a new SalesOrder in DRAFT status with an optional Kernel link.
     *
     * @param kernelSalesOrderId optional UUID reference to the Kernel order (null = informal)
     */
    public static TntSalesOrder create(UUID tenantId, UUID organizationId, UUID agencyId,
                                        UUID clientThirdPartyId, String orderNumber,
                                        List<TntSalesOrderLine> lines, TntAddress deliveryAddress,
                                        TntAddress billingAddress, OrderPriority priority,
                                        String currency, UUID kernelSalesOrderId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one order line is required");
        }
        BigDecimal subtotal = lines.stream()
                .map(TntSalesOrderLine::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now();
        return new TntSalesOrder(UUID.randomUUID(), tenantId, organizationId, agencyId,
                clientThirdPartyId, orderNumber, lines, deliveryAddress, billingAddress,
                SalesOrderStatus.DRAFT, priority, PaymentStatus.UNPAID, currency,
                subtotal, subtotal, null, null, kernelSalesOrderId,
                null, null,
                null, null, null, null, null, null, now, now);
    }

    /**
     * Rehydrates a TntSalesOrder from its persisted state (R2DBC mapping).
     */
    public static TntSalesOrder rehydrate(UUID id, UUID tenantId, UUID organizationId, UUID agencyId,
                                           UUID clientThirdPartyId, String orderNumber,
                                           List<TntSalesOrderLine> lines, TntAddress deliveryAddress,
                                           TntAddress billingAddress, SalesOrderStatus status,
                                           OrderPriority priority, PaymentStatus paymentStatus,
                                           String currency, BigDecimal subtotalAmount,
                                           BigDecimal totalAmount, UUID missionId, UUID invoiceId,
                                           UUID kernelSalesOrderId,
                                           String providerOrgType, String providerOrgId,
                                           ReturnReason returnReason, String returnNote,
                                           String cancelReason, Instant confirmedAt,
                                           Instant deliveredAt, Instant returnedAt,
                                           Instant createdAt, Instant updatedAt) {
        return new TntSalesOrder(id, tenantId, organizationId, agencyId, clientThirdPartyId,
                orderNumber, lines, deliveryAddress, billingAddress, status, priority, paymentStatus,
                currency, subtotalAmount, totalAmount, missionId, invoiceId, kernelSalesOrderId,
                providerOrgType, providerOrgId,
                returnReason, returnNote, cancelReason, confirmedAt, deliveredAt, returnedAt,
                createdAt, updatedAt);
    }

    // ─── State transitions ────────────────────────────────────────────────────

    /** DRAFT → CONFIRMED */
    public TntSalesOrder confirm() {
        requireStatus(SalesOrderStatus.DRAFT);
        Instant now = Instant.now();
        return copyWith(SalesOrderStatus.CONFIRMED, missionId, invoiceId,
                null, null, null, now, null, null, now);
    }

    /** CONFIRMED → STOCK_RESERVED */
    public TntSalesOrder reserveStock() {
        requireStatus(SalesOrderStatus.CONFIRMED);
        return copyWith(SalesOrderStatus.STOCK_RESERVED, missionId, invoiceId,
                null, null, null, confirmedAt, null, null, Instant.now());
    }

    /** STOCK_RESERVED → DISPATCHED + links delivery mission */
    public TntSalesOrder dispatch(UUID deliveryMissionId) {
        requireStatus(SalesOrderStatus.STOCK_RESERVED);
        Objects.requireNonNull(deliveryMissionId, "deliveryMissionId is required for dispatch");
        return copyWith(SalesOrderStatus.DISPATCHED, deliveryMissionId, invoiceId,
                null, null, null, confirmedAt, null, null, Instant.now());
    }

    /** DISPATCHED → IN_DELIVERY */
    public TntSalesOrder startDelivery() {
        requireStatus(SalesOrderStatus.DISPATCHED);
        return copyWith(SalesOrderStatus.IN_DELIVERY, missionId, invoiceId,
                null, null, null, confirmedAt, null, null, Instant.now());
    }

    /** IN_DELIVERY → DELIVERED */
    public TntSalesOrder markDelivered() {
        requireStatus(SalesOrderStatus.IN_DELIVERY);
        Instant now = Instant.now();
        return copyWith(SalesOrderStatus.DELIVERED, missionId, invoiceId,
                null, null, null, confirmedAt, now, null, now);
    }

    /** DISPATCHED | IN_DELIVERY → RETURNED */
    public TntSalesOrder markReturned(ReturnReason reason, String note) {
        if (status != SalesOrderStatus.IN_DELIVERY && status != SalesOrderStatus.DISPATCHED) {
            throw new IllegalStateException(
                    "Can only return an order that is DISPATCHED or IN_DELIVERY, current: " + status);
        }
        Instant now = Instant.now();
        return copyWith(SalesOrderStatus.RETURNED, missionId, invoiceId,
                reason, note, null, confirmedAt, null, now, now);
    }

    /** Any non-DELIVERED → CANCELLED */
    public TntSalesOrder cancel(String reason) {
        if (status == SalesOrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel a DELIVERED order");
        }
        return copyWith(SalesOrderStatus.CANCELLED, missionId, invoiceId,
                null, null, reason, confirmedAt, null, null, Instant.now());
    }

    /** Links an invoice to this order (any status). */
    public TntSalesOrder linkInvoice(UUID newInvoiceId) {
        Objects.requireNonNull(newInvoiceId, "invoiceId is required");
        return copyWith(status, missionId, newInvoiceId, returnReason, returnNote, cancelReason,
                confirmedAt, deliveredAt, returnedAt, Instant.now());
    }

    /** Marks the order as paid (payment lifecycle update). */
    public TntSalesOrder markPaid() {
        return new TntSalesOrder(id, tenantId, organizationId, agencyId, clientThirdPartyId,
                orderNumber, lines, deliveryAddress, billingAddress, status, priority,
                PaymentStatus.PAID, currency, subtotalAmount, totalAmount, missionId, invoiceId,
                kernelSalesOrderId, null, null,
                returnReason, returnNote, cancelReason,
                confirmedAt, deliveredAt, returnedAt, createdAt, Instant.now());
    }

    /**
     * Links this order to a Kernel sales order (optional — called during creation
     * when the Kernel equivalent is resolved).
     *
     * @param kernelSalesOrderId Kernel UUID to link
     * @return new instance with the Kernel link set
     */
    public TntSalesOrder withKernelSalesOrderId(UUID kernelSalesOrderId) {
        return new TntSalesOrder(id, tenantId, organizationId, agencyId, clientThirdPartyId,
                orderNumber, lines, deliveryAddress, billingAddress, status, priority, paymentStatus,
                currency, subtotalAmount, totalAmount, missionId, invoiceId,
                kernelSalesOrderId, providerOrgType, providerOrgId,
                returnReason, returnNote, cancelReason,
                confirmedAt, deliveredAt, returnedAt, createdAt, Instant.now());
    }

    /** Returns {@code true} if this order is linked to a Kernel record. */
    public boolean hasKernelLink() {
        return kernelSalesOrderId != null;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void requireStatus(SalesOrderStatus required) {
        if (status != required) {
            throw new IllegalStateException("Expected status " + required + " but was " + status);
        }
    }

    /**
     * Links this order to a FreelancerOrg as the executing logistics provider ().
     *
     * @param providerOrgType "AGENCY" or "FREELANCER_ORG"
     * @param providerOrgId   UUID of the providing org (from tnt-organization-core)
     * @return new order with provider context set
     */
    public TntSalesOrder withProviderOrg(String providerOrgType, String providerOrgId) {
        return new TntSalesOrder(id, tenantId, organizationId, agencyId, clientThirdPartyId,
                orderNumber, lines, deliveryAddress, billingAddress, status, priority, paymentStatus,
                currency, subtotalAmount, totalAmount, missionId, invoiceId,
                kernelSalesOrderId, providerOrgType, providerOrgId,
                returnReason, returnNote, cancelReason,
                confirmedAt, deliveredAt, returnedAt, createdAt, Instant.now());
    }

    /** Returns true if this order is executed by a FreelancerOrg. */
    public boolean isFreelancerOrgOrder() {
        return "FREELANCER_ORG".equals(providerOrgType);
    }

    private TntSalesOrder copyWith(SalesOrderStatus newStatus, UUID newMissionId, UUID newInvoiceId,
                                    ReturnReason newReturnReason, String newReturnNote,
                                    String newCancelReason, Instant newConfirmedAt,
                                    Instant newDeliveredAt, Instant newReturnedAt,
                                    Instant newUpdatedAt) {
        return new TntSalesOrder(id, tenantId, organizationId, agencyId, clientThirdPartyId,
                orderNumber, lines, deliveryAddress, billingAddress, newStatus, priority, paymentStatus,
                currency, subtotalAmount, totalAmount, newMissionId, newInvoiceId,
                kernelSalesOrderId, providerOrgType, providerOrgId,
                newReturnReason, newReturnNote, newCancelReason,
                newConfirmedAt, newDeliveredAt, newReturnedAt, createdAt, newUpdatedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getClientThirdPartyId() { return clientThirdPartyId; }
    public String getOrderNumber() { return orderNumber; }
    public List<TntSalesOrderLine> getLines() { return lines; }
    public TntAddress getDeliveryAddress() { return deliveryAddress; }
    public TntAddress getBillingAddress() { return billingAddress; }
    public SalesOrderStatus getStatus() { return status; }
    public OrderPriority getPriority() { return priority; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public UUID getMissionId() { return missionId; }
    public UUID getInvoiceId() { return invoiceId; }
    /** Optional Kernel integration key — may be {@code null}. */
    public UUID getKernelSalesOrderId() { return kernelSalesOrderId; }
    /** Type of the logistics service provider — AGENCY | FREELANCER_ORG. Nullable. */
    public String getProviderOrgType() { return providerOrgType; }
    /** UUID of the providing organization — FreelancerOrg or Agency. Nullable. */
    public String getProviderOrgId() { return providerOrgId; }
    public ReturnReason getReturnReason() { return returnReason; }
    public String getReturnNote() { return returnNote; }
    public String getCancelReason() { return cancelReason; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
