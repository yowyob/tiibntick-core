package com.yowyob.tiibntick.core.inventory.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity: InventoryMovement.
 *
 * Immutable audit record of every stock change applied to a StockEntry.
 * Provides a complete, traceable history of stock mutations including
 * the reason (reference document, order number, etc.).
 *
 * @author MANFOUO Braun.
 */
public final class InventoryMovement {

    private final UUID id;
    private final UUID tenantId;
    private final UUID stockEntryId;
    private final UUID productId;
    private final UUID warehouseId;
    private final MovementType type;
    private final double quantity;
    private final String reference;
    private final String notes;
    private final UUID performedBy;
    private final Instant occurredAt;

    private InventoryMovement(UUID id, UUID tenantId, UUID stockEntryId, UUID productId,
                               UUID warehouseId, MovementType type, double quantity,
                               String reference, String notes, UUID performedBy, Instant occurredAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.stockEntryId = Objects.requireNonNull(stockEntryId);
        this.productId = Objects.requireNonNull(productId);
        this.warehouseId = Objects.requireNonNull(warehouseId);
        this.type = Objects.requireNonNull(type);
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.quantity = quantity;
        this.reference = reference;
        this.notes = notes;
        this.performedBy = performedBy;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    public static InventoryMovement record(UUID tenantId, UUID stockEntryId, UUID productId,
                                            UUID warehouseId, MovementType type, double quantity,
                                            String reference, String notes, UUID performedBy) {
        return new InventoryMovement(UUID.randomUUID(), tenantId, stockEntryId, productId,
                warehouseId, type, quantity, reference, notes, performedBy, Instant.now());
    }

    public static InventoryMovement rehydrate(UUID id, UUID tenantId, UUID stockEntryId, UUID productId,
                                               UUID warehouseId, MovementType type, double quantity,
                                               String reference, String notes, UUID performedBy,
                                               Instant occurredAt) {
        return new InventoryMovement(id, tenantId, stockEntryId, productId, warehouseId, type,
                quantity, reference, notes, performedBy, occurredAt);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID stockEntryId() { return stockEntryId; }
    public UUID productId() { return productId; }
    public UUID warehouseId() { return warehouseId; }
    public MovementType type() { return type; }
    public double quantity() { return quantity; }
    public String reference() { return reference; }
    public String notes() { return notes; }
    public UUID performedBy() { return performedBy; }
    public Instant occurredAt() { return occurredAt; }
}
