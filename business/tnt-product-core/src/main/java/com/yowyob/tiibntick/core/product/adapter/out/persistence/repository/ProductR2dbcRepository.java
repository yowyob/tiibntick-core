package com.yowyob.tiibntick.core.product.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.product.adapter.out.persistence.entity.ProductEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ProductR2dbcRepository extends ReactiveCrudRepository<ProductEntity, UUID> {
    Flux<ProductEntity> findByTenantId(UUID tenantId);

    @Query("SELECT COUNT(*) > 0 FROM tnt_products WHERE tenant_id = :tenantId AND sku = :sku")
    Mono<Boolean> existsByTenantIdAndSku(UUID tenantId, String sku);
}
