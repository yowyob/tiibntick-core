package com.yowyob.tiibntick.core.product.application.port.out;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface ProductRepository {
    Mono<Product> save(Product product);
    Mono<Product> findById(UUID productId);
    Flux<Product> findByTenantId(UUID tenantId);
    Mono<Boolean> existsBySku(UUID tenantId, String sku);
}
