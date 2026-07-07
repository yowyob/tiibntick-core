package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.inventory.domain.model.InventoryMovement;
import com.yowyob.tiibntick.core.inventory.domain.model.MovementType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link InventoryMovement}.
 *
 * <p>Maps to the {@code tnt_inventory_movements} table. Every stock mutation in the
 * TiiBnTick inventory is recorded as an immutable audit trail entry here — enabling
 * full traceability for TiiBnTick Trust blockchain anchoring.</p>
 *
 * @author MANFOUO Braun
 */
@Table("tnt_inventory_movements")
public class InventoryMovementEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    private UUID tenantId;
    private UUID stockEntryId;
    private UUID productId;
    private UUID warehouseId;

    /** Movement type stored as VARCHAR(40) — mapped from/to {@link MovementType} enum. */
    private String type;

    private double quantity;
    private String reference;
    private String notes;
    private UUID performedBy;
    private Instant occurredAt;

    // ── Domain ↔ Entity mapping ──────────────────────────────────────────────

    /**
     * Converts a domain {@link InventoryMovement} to its persistence entity.
     *
     * @param m the domain entity
     * @return the R2DBC entity
     */
    public static InventoryMovementEntity fromDomain(InventoryMovement m) {
        InventoryMovementEntity e = new InventoryMovementEntity();
        e.id = m.id();
        e.tenantId = m.tenantId();
        e.stockEntryId = m.stockEntryId();
        e.productId = m.productId();
        e.warehouseId = m.warehouseId();
        e.type = m.type().name();
        e.quantity = m.quantity();
        e.reference = m.reference();
        e.notes = m.notes();
        e.performedBy = m.performedBy();
        e.occurredAt = m.occurredAt();
        return e;
    }

    /**
     * Rehydrates the domain {@link InventoryMovement} from its persisted state.
     *
     * @return the fully rehydrated domain entity
     */
    public InventoryMovement toDomain() {
        return InventoryMovement.rehydrate(id, tenantId, stockEntryId, productId,
                warehouseId, MovementType.valueOf(type), quantity,
                reference, notes, performedBy, occurredAt);
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getStockEntryId() { return stockEntryId; }
    public void setStockEntryId(UUID stockEntryId) { this.stockEntryId = stockEntryId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getPerformedBy() { return performedBy; }
    public void setPerformedBy(UUID performedBy) { this.performedBy = performedBy; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
