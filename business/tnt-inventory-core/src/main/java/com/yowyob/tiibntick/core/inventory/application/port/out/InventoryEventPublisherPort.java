package com.yowyob.tiibntick.core.inventory.application.port.out;
import com.yowyob.tiibntick.core.inventory.domain.event.StockLowEvent;
import reactor.core.publisher.Mono;
public interface InventoryEventPublisherPort {
    Mono<Void> publishStockLow(StockLowEvent event);
}
