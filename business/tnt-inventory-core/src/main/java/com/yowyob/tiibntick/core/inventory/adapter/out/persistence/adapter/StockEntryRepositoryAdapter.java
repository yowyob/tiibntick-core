package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.StockEntryEntity;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository.StockEntryR2dbcRepository;
import com.yowyob.tiibntick.core.inventory.application.port.out.StockEntryRepository;
import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
@Component
public class StockEntryRepositoryAdapter implements StockEntryRepository {
    private final StockEntryR2dbcRepository r2dbc;
    public StockEntryRepositoryAdapter(StockEntryR2dbcRepository r2dbc) { this.r2dbc = r2dbc; }
    @Override public Mono<StockEntry> save(StockEntry entry) { var _entity = StockEntryEntity.fromDomain(entry);
        return r2dbc.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbc.save(_entity);
                })
                .map(StockEntryEntity::toDomain); }
    @Override public Mono<StockEntry> findByProductAndWarehouse(UUID tenantId, UUID productId, UUID warehouseId) { return r2dbc.findByTenantIdAndProductIdAndWarehouseId(tenantId, productId, warehouseId).map(StockEntryEntity::toDomain); }
    @Override public Mono<StockEntry> findById(UUID id) { return r2dbc.findById(id).map(StockEntryEntity::toDomain); }
    @Override public Flux<StockEntry> findByTenantId(UUID tenantId) { return r2dbc.findByTenantId(tenantId).map(StockEntryEntity::toDomain); }
    @Override public Flux<StockEntry> findNeedingReorder(UUID tenantId) { return r2dbc.findNeedingReorder(tenantId).map(StockEntryEntity::toDomain); }
}
