package com.yowyob.tiibntick.core.inventory.application.port.in;
import reactor.core.publisher.Mono;
public interface ReceiveStockUseCase {
    Mono<Void> receiveStock(ReceiveStockCommand command);
}
