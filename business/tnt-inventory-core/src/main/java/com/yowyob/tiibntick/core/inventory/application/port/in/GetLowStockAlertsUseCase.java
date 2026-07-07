package com.yowyob.tiibntick.core.inventory.application.port.in;
import com.yowyob.tiibntick.core.inventory.domain.model.InventoryAlert;
import reactor.core.publisher.Flux;
import java.util.UUID;
public interface GetLowStockAlertsUseCase {
    Flux<InventoryAlert> getLowStockAlerts(UUID tenantId);
}
