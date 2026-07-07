package com.yowyob.tiibntick.core.product.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.product.adapter.out.persistence.entity.ServiceOfferEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;

public interface ServiceOfferR2dbcRepository extends ReactiveCrudRepository<ServiceOfferEntity, UUID> {
    Flux<ServiceOfferEntity> findByTenantIdAndProviderId(UUID tenantId, UUID providerId);

    @Query("SELECT * FROM tnt_service_offers WHERE tenant_id = :tenantId AND published_on_market = true AND status = 'ACTIVE'")
    Flux<ServiceOfferEntity> findPublishedByTenantId(UUID tenantId);

    @Query("SELECT * FROM tnt_service_offers WHERE tenant_id = :tenantId AND status = 'ACTIVE' AND published_on_market = true AND max_weight_kg >= :weightKg AND (max_distance_km IS NULL OR max_distance_km >= :distanceKm)")
    Flux<ServiceOfferEntity> findMatchingOffers(UUID tenantId, double weightKg, double distanceKm);
}
