package com.yowyob.tiibntick.core.product.application.port.in;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface GetProductUseCase {
    Mono<Product> getProduct(UUID productId);
}
