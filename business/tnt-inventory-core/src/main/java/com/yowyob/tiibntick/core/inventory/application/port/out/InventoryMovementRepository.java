package com.yowyob.tiibntick.core.inventory.application.port.out;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryMovement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface InventoryMovementRepository {
    Mono<InventoryMovement> save(InventoryMovement movement);
    Flux<InventoryMovement> findByStockEntry(UUID stockEntryId);
    Flux<InventoryMovement> findByProduct(UUID tenantId, UUID productId);
}
