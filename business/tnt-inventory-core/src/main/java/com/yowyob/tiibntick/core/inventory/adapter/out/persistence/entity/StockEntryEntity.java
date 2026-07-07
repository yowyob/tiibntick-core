package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link StockEntry}.
 *
 * <p>Maps to the {@code tnt_stock_entries} table. The {@code kernelStockEntryId}
 * column ({@code kernel_stock_entry_id}) stores the optional logical reference to the
 * Yowyob Kernel stock entry — there is no physical foreign key cross-database.</p>
 *
 * @author MANFOUO Braun
 */
@Table("tnt_stock_entries")
public class StockEntryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    private UUID tenantId;
    private UUID productId;
    private UUID warehouseId;

    /**
     * Optional logical reference to RT-comops-inventory-core stock entry.
     * NULL for informal/hub-only stock entries.
     * Column: kernel_stock_entry_id UUID (nullable).
     */
    @Column("kernel_stock_entry_id")
    private UUID kernelStockEntryId;

    private double quantity;
    private double reservedQuantity;
    private String unit;
    private Double reorderThreshold;
    private Instant lastMovementAt;
    private Instant createdAt;
    private Instant updatedAt;

    // ── Domain ↔ Entity mapping ──────────────────────────────────────────────

    /**
     * Converts a domain {@link StockEntry} to its persistence entity.
     *
     * @param s the domain aggregate
     * @return the entity ready for R2DBC persistence
     */
    public static StockEntryEntity fromDomain(StockEntry s) {
        StockEntryEntity e = new StockEntryEntity();
        e.id = s.id();
        e.tenantId = s.tenantId();
        e.productId = s.productId();
        e.warehouseId = s.warehouseId();
        e.kernelStockEntryId = s.kernelStockEntryId(); // nullable
        e.quantity = s.quantity();
        e.reservedQuantity = s.reservedQuantity();
        e.unit = s.unit();
        e.reorderThreshold = s.reorderThreshold();
        e.lastMovementAt = s.lastMovementAt();
        e.createdAt = s.createdAt();
        e.updatedAt = s.updatedAt();
        return e;
    }

    /**
     * Rehydrates the domain {@link StockEntry} from its persisted state.
     *
     * @return the fully rehydrated aggregate root
     */
    public StockEntry toDomain() {
        return StockEntry.rehydrate(id, tenantId, productId, warehouseId,
                kernelStockEntryId, // pass nullable Kernel reference
                quantity, reservedQuantity, unit, reorderThreshold,
                lastMovementAt, createdAt, updatedAt);
    }

    // ── Getters / Setters (required by Spring Data R2DBC) ────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }

    public UUID getKernelStockEntryId() { return kernelStockEntryId; }
    public void setKernelStockEntryId(UUID kernelStockEntryId) { this.kernelStockEntryId = kernelStockEntryId; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(double reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(Double reorderThreshold) { this.reorderThreshold = reorderThreshold; }

    public Instant getLastMovementAt() { return lastMovementAt; }
    public void setLastMovementAt(Instant lastMovementAt) { this.lastMovementAt = lastMovementAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
