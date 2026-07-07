package com.yowyob.tiibntick.core.inventory.application.port.in;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface ReleaseStockUseCase {
    Mono<Void> releaseStock(UUID tenantId, UUID productId, UUID warehouseId, double quantity, String reference);
}
