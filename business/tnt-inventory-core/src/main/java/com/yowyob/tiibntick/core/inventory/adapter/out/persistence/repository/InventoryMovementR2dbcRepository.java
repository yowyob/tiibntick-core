package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.InventoryMovementEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link InventoryMovementEntity}.
 *
 * <p>Provides reactive queries against the {@code tnt_inventory_movements} table.
 * Replaces the previous in-memory {@code ConcurrentHashMap} implementation that did not
 * survive restarts and was not suitable for production use.</p>
 *
 * @author MANFOUO Braun
 */
public interface InventoryMovementR2dbcRepository
        extends ReactiveCrudRepository<InventoryMovementEntity, UUID> {

    /**
     * Returns all movements for a given stock entry, ordered by occurrence time descending.
     *
     * @param stockEntryId the TNT stock entry UUID
     * @return reactive stream of movement entities
     */
    @Query("SELECT * FROM tnt_inventory_movements WHERE stock_entry_id = :stockEntryId ORDER BY occurred_at DESC")
    Flux<InventoryMovementEntity> findByStockEntryId(UUID stockEntryId);

    /**
     * Returns all movements for a product within a tenant, ordered by occurrence time descending.
     *
     * @param tenantId  tenant isolation key
     * @param productId TNT product UUID
     * @return reactive stream of movement entities
     */
    @Query("SELECT * FROM tnt_inventory_movements WHERE tenant_id = :tenantId AND product_id = :productId ORDER BY occurred_at DESC")
    Flux<InventoryMovementEntity> findByTenantIdAndProductId(UUID tenantId, UUID productId);
}
