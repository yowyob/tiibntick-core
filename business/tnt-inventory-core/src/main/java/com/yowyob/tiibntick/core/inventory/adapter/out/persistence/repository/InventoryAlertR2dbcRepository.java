package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.InventoryAlertEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@link InventoryAlertEntity}.
 *
 * <p>Provides reactive queries against the {@code tnt_inventory_alerts} table.
 * Replaces the previous in-memory implementation that did not persist across restarts.</p>
 *
 * @author MANFOUO Braun
 */
public interface InventoryAlertR2dbcRepository
        extends ReactiveCrudRepository<InventoryAlertEntity, UUID> {

    /**
     * Returns all unacknowledged alerts for a given tenant.
     *
     * <p>Used by operators and the notification system to identify products
     * requiring restocking or attention.</p>
     *
     * @param tenantId tenant isolation key
     * @return reactive stream of unacknowledged alert entities
     */
    @Query("SELECT * FROM tnt_inventory_alerts WHERE tenant_id = :tenantId AND acknowledged = FALSE ORDER BY triggered_at DESC")
    Flux<InventoryAlertEntity> findUnacknowledgedByTenantId(UUID tenantId);
}
