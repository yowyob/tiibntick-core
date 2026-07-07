package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.StockEntryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface StockEntryR2dbcRepository extends ReactiveCrudRepository<StockEntryEntity, UUID> {
    @Query("SELECT * FROM tnt_stock_entries WHERE tenant_id = :tenantId AND product_id = :productId AND warehouse_id = :warehouseId")
    Mono<StockEntryEntity> findByTenantIdAndProductIdAndWarehouseId(UUID tenantId, UUID productId, UUID warehouseId);
    Flux<StockEntryEntity> findByTenantId(UUID tenantId);
    @Query("SELECT * FROM tnt_stock_entries WHERE tenant_id = :tenantId AND reorder_threshold IS NOT NULL AND (quantity - reserved_quantity) <= reorder_threshold")
    Flux<StockEntryEntity> findNeedingReorder(UUID tenantId);
}
