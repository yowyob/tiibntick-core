package com.yowyob.tiibntick.core.inventory.application.port.out;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface InventoryAlertRepository {
    Mono<InventoryAlert> save(InventoryAlert alert);
    Flux<InventoryAlert> findUnacknowledgedByTenant(UUID tenantId);
}
