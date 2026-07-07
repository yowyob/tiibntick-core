package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.InventoryMovementEntity;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository.InventoryMovementR2dbcRepository;
import com.yowyob.tiibntick.core.inventory.application.port.out.InventoryMovementRepository;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryMovement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter for {@link InventoryMovementRepository}.
 *
 * <p>Implements the outbound port using Spring Data R2DBC backed by the
 * {@code tnt_inventory_movements} PostgreSQL table. This replaces the previous
 * in-memory {@code ConcurrentHashMap} implementation that did not persist across
 * restarts and was not production-ready.</p>
 *
 * <p>All methods are reactive (non-blocking) using {@code Mono} and {@code Flux}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class InventoryMovementRepositoryAdapter implements InventoryMovementRepository {

    private final InventoryMovementR2dbcRepository r2dbc;

    public InventoryMovementRepositoryAdapter(InventoryMovementR2dbcRepository r2dbc) {
        this.r2dbc = r2dbc;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain entity to its R2DBC form, persists it, and maps back
     * to the domain entity on success.</p>
     */
    @Override
    public Mono<InventoryMovement> save(InventoryMovement movement) {
        var _entity = InventoryMovementEntity.fromDomain(movement);
        return r2dbc.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbc.save(_entity);
                })
                .map(InventoryMovementEntity::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns all movements for the given stock entry ordered by occurrence time descending.</p>
     */
    @Override
    public Flux<InventoryMovement> findByStockEntry(UUID stockEntryId) {
        return r2dbc.findByStockEntryId(stockEntryId)
                .map(InventoryMovementEntity::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns all movements for the given product in the tenant context,
     * ordered by occurrence time descending.</p>
     */
    @Override
    public Flux<InventoryMovement> findByProduct(UUID tenantId, UUID productId) {
        return r2dbc.findByTenantIdAndProductId(tenantId, productId)
                .map(InventoryMovementEntity::toDomain);
    }
}
