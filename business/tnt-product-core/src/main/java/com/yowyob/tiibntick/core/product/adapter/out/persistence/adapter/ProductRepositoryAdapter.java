package com.yowyob.tiibntick.core.product.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.product.adapter.out.persistence.entity.ProductEntity;
import com.yowyob.tiibntick.core.product.adapter.out.persistence.repository.ProductR2dbcRepository;
import com.yowyob.tiibntick.core.product.application.port.out.ProductRepository;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
public class ProductRepositoryAdapter implements ProductRepository {
    private final ProductR2dbcRepository r2dbc;
    public ProductRepositoryAdapter(ProductR2dbcRepository r2dbc) { this.r2dbc = r2dbc; }

    @Override
    public Mono<Product> save(Product product) {
        var _entity = ProductEntity.fromDomain(product);
        return r2dbc.existsById(_entity.getId())
                .flatMap(exists -> {
                    _entity.setNew(!exists);
                    return r2dbc.save(_entity);
                })
                .map(ProductEntity::toDomain);
    }

    @Override
    public Mono<Product> findById(UUID productId) {
        return r2dbc.findById(productId).map(ProductEntity::toDomain);
    }

    @Override
    public Flux<Product> findByTenantId(UUID tenantId) {
        return r2dbc.findByTenantId(tenantId).map(ProductEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsBySku(UUID tenantId, String sku) {
        return r2dbc.existsByTenantIdAndSku(tenantId, sku);
    }
}
