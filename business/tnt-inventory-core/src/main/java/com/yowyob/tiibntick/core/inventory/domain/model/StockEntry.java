package com.yowyob.tiibntick.core.inventory.domain.model;

import com.yowyob.tiibntick.core.inventory.domain.exception.InsufficientStockException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: StockEntry.
 *
 * <p>Tracks the available and reserved quantity of a product in a specific warehouse
 * within the TiiBnTick logistics context. All mutations return new immutable instances.
 * Quantity invariants (non-negative stock, reserved ≤ total) are enforced by domain
 * methods — no quantity can go below zero.</p>
 *
 * <p><b>Kernel integration:</b> {@code kernelStockEntryId} is an optional logical
 * reference to the matching stock entry in the Yowyob Kernel (RT-comops-inventory-core).
 * It is {@code null} for informal or hub-only stock entries that have no ERP counterpart.
 * There is <em>no Java inheritance</em> from Kernel stock classes — the link is purely
 * a UUID reference stored in {@code tnt_stock_entries.kernel_stock_entry_id}.</p>
 *
 * @author MANFOUO Braun
 */
public final class StockEntry {

    /** Primary key in tnt_core_db.tnt_stock_entries. */
    private final UUID id;

    /** Multi-tenant isolation key. */
    private final UUID tenantId;

    /**
     * TiiBnTick product UUID.
     * Corresponds to tnt-product-core catalogProductId, itself linked to the Kernel product.
     */
    private final UUID productId;

    /** UUID of the warehouse or relay hub storage location within TNT. */
    private final UUID warehouseId;

    /**
     * Optional logical reference to the Kernel stock entry (RT-comops-inventory-core).
     * {@code null} when the stock entry is informal (e.g. hub-only storage slot).
     * No physical FK cross-database — logical reference only.
     */
    private final UUID kernelStockEntryId;

    /** Total physical quantity on hand (including reserved). */
    private final double quantity;

    /** Quantity locked for pending orders — subset of {@code quantity}. */
    private final double reservedQuantity;

    /** Unit of measure (e.g. "UNIT", "KG", "BOX"). */
    private final String unit;

    /** Optional threshold below which a reorder alert is triggered. */
    private final Double reorderThreshold;

    /** Timestamp of the last stock movement (null if never moved). */
    private final Instant lastMovementAt;

    private final Instant createdAt;
    private final Instant updatedAt;

    private StockEntry(UUID id, UUID tenantId, UUID productId, UUID warehouseId,
                       UUID kernelStockEntryId,
                       double quantity, double reservedQuantity, String unit,
                       Double reorderThreshold, Instant lastMovementAt,
                       Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        this.kernelStockEntryId = kernelStockEntryId; // nullable — optional Kernel link
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        if (reservedQuantity < 0) throw new IllegalArgumentException("reservedQuantity must be >= 0");
        if (reservedQuantity > quantity) throw new IllegalArgumentException(
                "reservedQuantity(" + reservedQuantity + ") cannot exceed quantity(" + quantity + ")");
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.reorderThreshold = reorderThreshold;
        this.lastMovementAt = lastMovementAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Creates a new stock entry with zero quantities.
     *
     * @param tenantId           tenant isolation key
     * @param productId          TNT product UUID
     * @param warehouseId        storage location UUID
     * @param unit               unit of measure
     * @param reorderThreshold   optional alert threshold (null = no alerts)
     * @param kernelStockEntryId optional Kernel stock entry UUID (null for informal stock)
     * @return new StockEntry with zero quantities
     */
    public static StockEntry create(UUID tenantId, UUID productId, UUID warehouseId,
                                    String unit, Double reorderThreshold,
                                    UUID kernelStockEntryId) {
        Instant now = Instant.now();
        return new StockEntry(UUID.randomUUID(), tenantId, productId, warehouseId,
                kernelStockEntryId, 0.0, 0.0, unit, reorderThreshold, null, now, now);
    }

    /**
     * Convenience factory without Kernel link (informal stock).
     *
     * @see #create(UUID, UUID, UUID, String, Double, UUID)
     */
    public static StockEntry create(UUID tenantId, UUID productId, UUID warehouseId,
                                    String unit, Double reorderThreshold) {
        return create(tenantId, productId, warehouseId, unit, reorderThreshold, null);
    }

    /**
     * Rehydrates a StockEntry from its persisted state (R2DBC mapping).
     */
    public static StockEntry rehydrate(UUID id, UUID tenantId, UUID productId, UUID warehouseId,
                                       UUID kernelStockEntryId,
                                       double quantity, double reservedQuantity, String unit,
                                       Double reorderThreshold, Instant lastMovementAt,
                                       Instant createdAt, Instant updatedAt) {
        return new StockEntry(id, tenantId, productId, warehouseId, kernelStockEntryId,
                quantity, reservedQuantity, unit, reorderThreshold, lastMovementAt,
                createdAt, updatedAt);
    }

    // ── Domain mutations ────────────────────────────────────────────────────

    /** Receive stock — increases physical quantity. */
    public StockEntry receive(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        return withQuantities(quantity + amount, reservedQuantity);
    }

    /** Consume stock — decreases physical quantity (irreversible). */
    public StockEntry consume(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (availableQuantity() < amount) {
            throw new InsufficientStockException(productId, warehouseId, amount, availableQuantity());
        }
        return withQuantities(quantity - amount, reservedQuantity);
    }

    /** Reserve stock — locks quantity for a future consume (e.g. order dispatch). */
    public StockEntry reserve(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (availableQuantity() < amount) {
            throw new InsufficientStockException(productId, warehouseId, amount, availableQuantity());
        }
        return withQuantities(quantity, reservedQuantity + amount);
    }

    /** Release previously reserved stock back to available. */
    public StockEntry releaseReservation(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        double newReserved = Math.max(0, reservedQuantity - amount);
        return withQuantities(quantity, newReserved);
    }

    /** Dispatch reserved stock — consumes the reserved quantity atomically. */
    public StockEntry dispatchReserved(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (reservedQuantity < amount) throw new IllegalArgumentException(
                "Cannot dispatch more than reserved: " + amount + " > " + reservedQuantity);
        return withQuantities(quantity - amount, reservedQuantity - amount);
    }

    /**
     * Adjust quantity by a signed delta (positive = addition, negative = correction).
     * The resulting total must remain >= reserved quantity.
     */
    public StockEntry adjust(double delta) {
        double newQty = quantity + delta;
        if (newQty < reservedQuantity) throw new IllegalArgumentException(
                "Adjustment would bring total below reserved quantity");
        if (newQty < 0) throw new IllegalArgumentException("Adjustment would make stock negative");
        return withQuantities(newQty, reservedQuantity);
    }

    /**
     * Links this stock entry to a Kernel stock entry (optional).
     * Used when the Kernel equivalent is discovered after initial creation.
     *
     * @param kernelStockEntryId Kernel UUID to link
     * @return new instance with the Kernel link set
     */
    public StockEntry withKernelStockEntryId(UUID kernelStockEntryId) {
        return new StockEntry(id, tenantId, productId, warehouseId, kernelStockEntryId,
                quantity, reservedQuantity, unit, reorderThreshold,
                lastMovementAt, createdAt, Instant.now());
    }

    // ── Query helpers ────────────────────────────────────────────────────────

    /** Returns the quantity that can be freely allocated (total − reserved). */
    public double availableQuantity() {
        return quantity - reservedQuantity;
    }

    /** Returns {@code true} if available quantity is at or below the reorder threshold. */
    public boolean needsReorder() {
        return reorderThreshold != null && availableQuantity() <= reorderThreshold;
    }

    /** Returns {@code true} if available quantity is 0. */
    public boolean isOutOfStock() {
        return availableQuantity() <= 0;
    }

    /** Returns {@code true} if this stock entry is linked to a Kernel record. */
    public boolean hasKernelLink() {
        return kernelStockEntryId != null;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private StockEntry withQuantities(double newQty, double newReserved) {
        return new StockEntry(id, tenantId, productId, warehouseId, kernelStockEntryId,
                newQty, newReserved, unit, reorderThreshold, Instant.now(),
                createdAt, Instant.now());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID productId() { return productId; }
    public UUID warehouseId() { return warehouseId; }
    /** Optional Kernel integration key — may be {@code null}. */
    public UUID kernelStockEntryId() { return kernelStockEntryId; }
    public double quantity() { return quantity; }
    public double reservedQuantity() { return reservedQuantity; }
    public String unit() { return unit; }
    public Double reorderThreshold() { return reorderThreshold; }
    public Instant lastMovementAt() { return lastMovementAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
