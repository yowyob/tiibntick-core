package com.yowyob.tiibntick.core.product.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(UUID productId, UUID tenantId, String sku, String name, Instant occurredAt) {
    public static ProductCreatedEvent of(UUID productId, UUID tenantId, String sku, String name) {
        return new ProductCreatedEvent(productId, tenantId, sku, name, Instant.now());
    }
}
