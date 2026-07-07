package com.yowyob.tiibntick.core.inventory.application.port.out;
import com.yowyob.tiibntick.core.inventory.domain.event.PackageDepositedEvent;
import com.yowyob.tiibntick.core.inventory.domain.event.PackagePickedUpEvent;
import com.yowyob.tiibntick.core.inventory.domain.event.StockLowEvent;
import reactor.core.publisher.Mono;
public interface InventoryEventPublisherPort {
    Mono<Void> publishStockLow(StockLowEvent event);
    Mono<Void> publishPackageDeposited(PackageDepositedEvent event);
    Mono<Void> publishPackagePickedUp(PackagePickedUpEvent event);
}
