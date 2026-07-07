package com.yowyob.tiibntick.core.inventory.domain.model;

import java.util.UUID;

/**
 * Read-only DTO representing a stock entry fetched from the Yowyob Kernel
 * (RT-comops-inventory-core) via the KernelInventoryPort.
 *
 * <p>This record is <b>never persisted</b> in tnt_core_db. It is used exclusively
 * for enrichment and existence validation at application layer before recording
 * a TNT stock entry with its {@code kernelStockEntryId} reference key.</p>
 *
 * <p>Integration pattern: optional coupling — a TiiBnTick StockEntry may exist
 * without a Kernel counterpart (e.g. informal hub slots with no ERP registration).</p>
 *
 * @param kernelStockEntryId UUID of the stock entry in the Kernel database
 * @param productId          Kernel product UUID (matches tnt-product-core catalogProductId)
 * @param warehouseId        Kernel warehouse/location UUID
 * @param quantity           Current stock quantity in the Kernel
 * @param unit               Unit of measure defined in the Kernel catalogue
 * @param tenantId           Tenant owning this stock entry in the Kernel
 * @param isActive           Whether the Kernel stock entry is active
 *
 * @author MANFOUO Braun
 */
public record KernelStockEntryDto(
        UUID kernelStockEntryId,
        UUID productId,
        UUID warehouseId,
        double quantity,
        String unit,
        UUID tenantId,
        boolean isActive
) {}
