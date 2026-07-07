package com.yowyob.tiibntick.core.product.application.port.in;

import com.yowyob.tiibntick.core.product.domain.model.Product;
import reactor.core.publisher.Mono;

public interface CreateProductUseCase {
    Mono<Product> createProduct(CreateProductCommand command);
}
