package com.yowyob.tiibntick.core.inventory.application.port.in;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface ConsumeStockUseCase {
    Mono<Void> consumeStock(UUID tenantId, UUID productId, UUID warehouseId, double quantity, String reference, UUID performedBy);
}
