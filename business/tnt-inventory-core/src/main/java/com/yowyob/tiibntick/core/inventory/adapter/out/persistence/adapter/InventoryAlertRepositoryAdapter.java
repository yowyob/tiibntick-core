package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.InventoryAlertEntity;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository.InventoryAlertR2dbcRepository;
import com.yowyob.tiibntick.core.inventory.application.port.out.InventoryAlertRepository;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryAlert;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence adapter for {@link InventoryAlertRepository}.
 *
 * <p>Implements the outbound port using Spring Data R2DBC backed by the
 * {@code tnt_inventory_alerts} PostgreSQL table. This replaces the previous
 * in-memory {@code ConcurrentHashMap} implementation that lost all alert history
 * on application restart and was not suitable for production.</p>
 *
 * <p>Alerts persist across restarts and can be acknowledged by operators via the
 * TiiBnTick Agency or Market dashboards.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class InventoryAlertRepositoryAdapter implements InventoryAlertRepository {

    private final InventoryAlertR2dbcRepository r2dbc;

    public InventoryAlertRepositoryAdapter(InventoryAlertR2dbcRepository r2dbc) {
        this.r2dbc = r2dbc;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inserts or updates (upserts) the alert record.
     * Spring Data R2DBC uses INSERT for new IDs, UPDATE for existing ones.</p>
     */
    @Override
    public Mono<InventoryAlert> save(InventoryAlert alert) {
        var _entity = InventoryAlertEntity.fromDomain(alert);
        return r2dbc.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbc.save(_entity);
                })
                .map(InventoryAlertEntity::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns all unacknowledged alerts for the tenant ordered by triggered_at DESC.</p>
     */
    @Override
    public Flux<InventoryAlert> findUnacknowledgedByTenant(UUID tenantId) {
        return r2dbc.findUnacknowledgedByTenantId(tenantId)
                .map(InventoryAlertEntity::toDomain);
    }
}
