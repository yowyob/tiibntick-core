package com.yowyob.tiibntick.core.inventory.domain.exception;
import java.util.UUID;
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(UUID productId, UUID warehouseId, double requested, double available) {
        super("Insufficient stock for product " + productId + " in warehouse " + warehouseId +
              ": requested=" + requested + " available=" + available);
    }
}
