package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.inventory.domain.model.AlertType;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryAlert;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link InventoryAlert}.
 *
 * <p>Maps to the {@code tnt_inventory_alerts} table. Alerts are persistent
 * records — they survive restarts and can be acknowledged by operators.</p>
 *
 * @author MANFOUO Braun
 */
@Table("tnt_inventory_alerts")
public class InventoryAlertEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    private UUID tenantId;
    private UUID productId;
    private UUID warehouseId;

    /** Alert type stored as VARCHAR(30) — mapped from/to {@link AlertType} enum. */
    private String type;

    private double currentQuantity;
    private Double threshold;
    private boolean acknowledged;
    private Instant triggeredAt;
    private Instant acknowledgedAt;

    // ── Domain ↔ Entity mapping ──────────────────────────────────────────────

    /**
     * Converts a domain {@link InventoryAlert} to its R2DBC entity.
     *
     * @param a the domain entity
     * @return the entity ready for persistence
     */
    public static InventoryAlertEntity fromDomain(InventoryAlert a) {
        InventoryAlertEntity e = new InventoryAlertEntity();
        e.id = a.id();
        e.tenantId = a.tenantId();
        e.productId = a.productId();
        e.warehouseId = a.warehouseId();
        e.type = a.type().name();
        e.currentQuantity = a.currentQuantity();
        e.threshold = a.threshold();
        e.acknowledged = a.isAcknowledged();
        e.triggeredAt = a.triggeredAt();
        e.acknowledgedAt = a.acknowledgedAt();
        return e;
    }

    /**
     * Rehydrates the domain {@link InventoryAlert} from its persisted state.
     *
     * @return the domain entity
     */
    public InventoryAlert toDomain() {
        return InventoryAlert.rehydrate(id, tenantId, productId, warehouseId,
                AlertType.valueOf(type), currentQuantity, threshold,
                acknowledged, triggeredAt, acknowledgedAt);
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(double currentQuantity) { this.currentQuantity = currentQuantity; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
