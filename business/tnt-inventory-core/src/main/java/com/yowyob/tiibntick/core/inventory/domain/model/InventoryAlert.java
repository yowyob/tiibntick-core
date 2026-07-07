package com.yowyob.tiibntick.core.inventory.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class InventoryAlert {

    private final UUID id;
    private final UUID tenantId;
    private final UUID productId;
    private final UUID warehouseId;
    private final AlertType type;
    private final double currentQuantity;
    private final Double threshold;
    private final boolean acknowledged;
    private final Instant triggeredAt;
    private final Instant acknowledgedAt;

    private InventoryAlert(UUID id, UUID tenantId, UUID productId, UUID warehouseId,
                           AlertType type, double currentQuantity, Double threshold,
                           boolean acknowledged, Instant triggeredAt, Instant acknowledgedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.productId = Objects.requireNonNull(productId);
        this.warehouseId = Objects.requireNonNull(warehouseId);
        this.type = Objects.requireNonNull(type);
        this.currentQuantity = currentQuantity;
        this.threshold = threshold;
        this.acknowledged = acknowledged;
        this.triggeredAt = Objects.requireNonNull(triggeredAt);
        this.acknowledgedAt = acknowledgedAt;
    }

    public static InventoryAlert trigger(UUID tenantId, UUID productId, UUID warehouseId,
                                          AlertType type, double currentQuantity, Double threshold) {
        return new InventoryAlert(UUID.randomUUID(), tenantId, productId, warehouseId, type,
                currentQuantity, threshold, false, Instant.now(), null);
    }

    public static InventoryAlert rehydrate(UUID id, UUID tenantId, UUID productId, UUID warehouseId,
                                            AlertType type, double currentQuantity, Double threshold,
                                            boolean acknowledged, Instant triggeredAt, Instant acknowledgedAt) {
        return new InventoryAlert(id, tenantId, productId, warehouseId, type, currentQuantity,
                threshold, acknowledged, triggeredAt, acknowledgedAt);
    }

    public InventoryAlert acknowledge() {
        return new InventoryAlert(id, tenantId, productId, warehouseId, type, currentQuantity,
                threshold, true, triggeredAt, Instant.now());
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID productId() { return productId; }
    public UUID warehouseId() { return warehouseId; }
    public AlertType type() { return type; }
    public double currentQuantity() { return currentQuantity; }
    public Double threshold() { return threshold; }
    public boolean isAcknowledged() { return acknowledged; }
    public Instant triggeredAt() { return triggeredAt; }
    public Instant acknowledgedAt() { return acknowledgedAt; }
}
