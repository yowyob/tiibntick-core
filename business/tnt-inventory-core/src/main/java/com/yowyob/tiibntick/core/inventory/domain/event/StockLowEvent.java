package com.yowyob.tiibntick.core.inventory.domain.event;
import java.time.Instant;
import java.util.UUID;
public record StockLowEvent(UUID productId, UUID warehouseId, UUID tenantId, double currentQuantity, double threshold, Instant occurredAt) {
    public static StockLowEvent of(UUID productId, UUID warehouseId, UUID tenantId, double qty, double threshold) {
        return new StockLowEvent(productId, warehouseId, tenantId, qty, threshold, Instant.now());
    }
}
