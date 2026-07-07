package com.yowyob.tiibntick.core.product.application.port.in;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import reactor.core.publisher.Flux;
import java.util.UUID;
public interface ListProductsByTenantUseCase {
    Flux<Product> listProductsByTenant(UUID tenantId);
}
