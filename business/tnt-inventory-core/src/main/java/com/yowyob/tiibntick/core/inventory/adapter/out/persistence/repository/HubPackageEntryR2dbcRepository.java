package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity.HubPackageEntryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface HubPackageEntryR2dbcRepository extends ReactiveCrudRepository<HubPackageEntryEntity, UUID> {
    Mono<HubPackageEntryEntity> findByTrackingCode(String trackingCode);
    @Query("SELECT * FROM tnt_hub_package_entries WHERE hub_id = :hubId AND picked_up_at IS NULL")
    Flux<HubPackageEntryEntity> findActiveByHubId(UUID hubId);
    Flux<HubPackageEntryEntity> findByHubId(UUID hubId);
}
