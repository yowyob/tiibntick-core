package com.yowyob.tiibntick.core.inventory.application.port.in;
import java.util.UUID;
public record ReceiveStockCommand(UUID tenantId, UUID productId, UUID warehouseId, double quantity, String reference, String notes, UUID performedBy) {}
