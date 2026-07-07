package com.yowyob.tiibntick.core.inventory.application.port.in;
import reactor.core.publisher.Mono;
public interface ReserveStockUseCase {
    Mono<Void> reserveStock(ReserveStockCommand command);
}
