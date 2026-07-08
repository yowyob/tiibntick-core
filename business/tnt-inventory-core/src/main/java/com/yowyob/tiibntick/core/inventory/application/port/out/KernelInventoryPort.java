package com.yowyob.tiibntick.core.inventory.application.port.out;

import com.yowyob.tiibntick.core.inventory.domain.model.KernelStockEntryDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel Inventory Bridge.
 *
 * <p>Defines the contract for querying the Yowyob Kernel (RT-comops-inventory-core)
 * about stock entries. TiiBnTick does NOT inherit or extend Kernel inventory classes.
 * Instead, it references Kernel stock entries by their UUID ({@code kernelStockEntryId})
 * and only accesses the Kernel when needed for validation or enrichment.</p>
 *
 * <p>The coupling is intentionally <em>optional</em>: a TNT stock entry can exist
 * with a {@code null} {@code kernelStockEntryId} for informal/hub-only stock.</p>
 *
 * <p>Implementations: {@link com.yowyob.tiibntick.core.inventory.adapter.out.kernel.KernelInventoryAdapter}
 * (reactive WebClient over the Kernel REST API).</p>
 *
 * @author MANFOUO Braun
 */
public interface KernelInventoryPort {

    /**
     * Checks whether a stock entry exists in the Kernel for the given product and warehouse.
     *
     * <p>Used by the application service to optionally resolve a {@code kernelStockEntryId}
     * before persisting a new TNT stock entry.</p>
     *
     * @param productId   the Kernel product UUID
     * @param warehouseId the Kernel warehouse/location UUID
     * @param tenantId    tenant context
     * @return the matching Kernel stock entry, or {@code Mono.empty()} if none found
     */
    Mono<KernelStockEntryDto> findByProductAndWarehouse(UUID productId, UUID warehouseId, UUID tenantId);
}
