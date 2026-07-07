package com.yowyob.tiibntick.core.inventory.domain.exception;
import java.util.UUID;
public class StockEntryNotFoundException extends RuntimeException {
    public StockEntryNotFoundException(UUID productId, UUID warehouseId) {
        super("Stock entry not found for product " + productId + " in warehouse " + warehouseId);
    }
    public StockEntryNotFoundException(UUID id) {
        super("Stock entry not found: " + id);
    }
}
