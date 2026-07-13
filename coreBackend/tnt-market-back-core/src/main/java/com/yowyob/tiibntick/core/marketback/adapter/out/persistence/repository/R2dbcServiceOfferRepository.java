package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.ServiceOfferEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * R2DBC repository for {@link ServiceOfferEntity} — tnt_market.service_offers.
 * Ported from the standalone app's {@code R2dbcServiceOfferRepository}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcServiceOfferRepository extends ReactiveCrudRepository<ServiceOfferEntity, UUID> {

    Mono<ServiceOfferEntity> findByIdAndTenantId(UUID id, String tenantId);

    Flux<ServiceOfferEntity> findByListingId(UUID listingId);

    Flux<ServiceOfferEntity> findByListingIdAndStatus(UUID listingId, String status);

    Flux<ServiceOfferEntity> findByListingIdAndTenantId(UUID listingId, String tenantId);

    Flux<ServiceOfferEntity> findByListingIdAndStatusAndTenantId(UUID listingId, String status, String tenantId);

    Flux<ServiceOfferEntity> findByServiceTypeAndTenantId(String serviceType, String tenantId);

    Flux<ServiceOfferEntity> findByProviderIdAndStatusAndTenantId(UUID providerId, String status, String tenantId);

    @Query("SELECT * FROM tnt_market.service_offers WHERE tenant_id = :tenantId AND currency = :currency "
            + "AND base_price BETWEEN :minPrice AND :maxPrice")
    Flux<ServiceOfferEntity> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, String currency, String tenantId);
}
