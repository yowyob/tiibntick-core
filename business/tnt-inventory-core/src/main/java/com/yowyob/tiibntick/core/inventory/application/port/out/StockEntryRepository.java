package com.yowyob.tiibntick.core.inventory.application.port.out;
import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface StockEntryRepository {
    Mono<StockEntry> save(StockEntry entry);
    Mono<StockEntry> findByProductAndWarehouse(UUID tenantId, UUID productId, UUID warehouseId);
    Mono<StockEntry> findById(UUID id);
    Flux<StockEntry> findByTenantId(UUID tenantId);
    Flux<StockEntry> findNeedingReorder(UUID tenantId);
}
