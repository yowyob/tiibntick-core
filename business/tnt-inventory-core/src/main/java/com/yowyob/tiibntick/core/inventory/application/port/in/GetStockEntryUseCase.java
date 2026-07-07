package com.yowyob.tiibntick.core.inventory.application.port.in;
import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface GetStockEntryUseCase {
    Mono<StockEntry> getStockEntry(UUID tenantId, UUID productId, UUID warehouseId);
}
