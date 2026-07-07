package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.HubPackageEntryEntity;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository.HubPackageEntryR2dbcRepository;
import com.yowyob.tiibntick.core.inventory.application.port.out.HubPackageEntryRepository;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
@Component
public class HubPackageEntryRepositoryAdapter implements HubPackageEntryRepository {
    private final HubPackageEntryR2dbcRepository r2dbc;
    public HubPackageEntryRepositoryAdapter(HubPackageEntryR2dbcRepository r2dbc) { this.r2dbc = r2dbc; }
    @Override public Mono<HubPackageEntry> save(HubPackageEntry entry) { var _entity = HubPackageEntryEntity.fromDomain(entry);
        return r2dbc.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbc.save(_entity);
                })
                .map(HubPackageEntryEntity::toDomain); }
    @Override public Mono<HubPackageEntry> findById(UUID id) { return r2dbc.findById(id).map(HubPackageEntryEntity::toDomain); }
    @Override public Mono<HubPackageEntry> findByTrackingCode(String trackingCode) { return r2dbc.findByTrackingCode(trackingCode).map(HubPackageEntryEntity::toDomain); }
    @Override public Flux<HubPackageEntry> findActiveByHub(UUID hubId) { return r2dbc.findActiveByHubId(hubId).map(HubPackageEntryEntity::toDomain); }
    @Override public Flux<HubPackageEntry> findAllByHub(UUID hubId) { return r2dbc.findByHubId(hubId).map(HubPackageEntryEntity::toDomain); }
}
