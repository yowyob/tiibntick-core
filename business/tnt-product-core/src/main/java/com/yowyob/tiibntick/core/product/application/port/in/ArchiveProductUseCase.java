package com.yowyob.tiibntick.core.product.application.port.in;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface ArchiveProductUseCase {
    Mono<Void> archiveProduct(UUID productId);
}
