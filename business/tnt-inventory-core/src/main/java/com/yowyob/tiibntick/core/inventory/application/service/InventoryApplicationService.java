package com.yowyob.tiibntick.core.inventory.application.service;

import com.yowyob.tiibntick.core.inventory.application.port.in.*;
import com.yowyob.tiibntick.core.inventory.application.port.out.*;
import com.yowyob.tiibntick.core.inventory.domain.event.StockLowEvent;
import com.yowyob.tiibntick.core.inventory.domain.exception.StockEntryNotFoundException;
import com.yowyob.tiibntick.core.inventory.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for inventory stock management.
 *
 * <p>Coordinates stock operations (receive, reserve, release, consume) with movement
 * recording and automatic alert generation when stock falls below the reorder threshold.</p>
 *
 * <p><b>Kernel integration:</b> When creating a new stock entry, this service optionally
 * queries the Kernel (RT-comops-inventory-core) via {@link KernelInventoryPort} to resolve
 * a matching {@code kernelStockEntryId}. The Kernel link is non-blocking and optional —
 * the TNT stock entry is always created even when no Kernel counterpart exists.</p>
 *
 * @author MANFOUO Braun
 */
@Service("tntInventoryService")
public class InventoryApplicationService implements
        ReserveStockUseCase,
        ReleaseStockUseCase,
        ReceiveStockUseCase,
        ConsumeStockUseCase,
        GetStockEntryUseCase,
        GetLowStockAlertsUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryApplicationService.class);

    private final StockEntryRepository stockEntryRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryAlertRepository alertRepository;
    private final InventoryEventPublisherPort eventPublisher;

    /**
     * Outbound port to the Yowyob Kernel inventory.
     * Optional — injected with a lenient qualifier to allow null-safe usage.
     */
    private final KernelInventoryPort kernelInventoryPort;

    public InventoryApplicationService(StockEntryRepository stockEntryRepository,
                                        InventoryMovementRepository movementRepository,
                                        InventoryAlertRepository alertRepository,
                                        InventoryEventPublisherPort eventPublisher,
                                        KernelInventoryPort kernelInventoryPort) {
        this.stockEntryRepository = stockEntryRepository;
        this.movementRepository = movementRepository;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
        this.kernelInventoryPort = kernelInventoryPort;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If no stock entry exists yet for the product/warehouse combination, a new one
     * is created. The Kernel is queried asynchronously to attempt linking a
     * {@code kernelStockEntryId} before the first movement is recorded.</p>
     */
    @Override
    public Mono<Void> receiveStock(ReceiveStockCommand cmd) {
        return stockEntryRepository.findByProductAndWarehouse(
                        cmd.tenantId(), cmd.productId(), cmd.warehouseId())
                .switchIfEmpty(resolveOrCreateStockEntry(cmd))
                .flatMap(entry -> {
                    StockEntry updated = entry.receive(cmd.quantity());
                    InventoryMovement movement = InventoryMovement.record(
                            cmd.tenantId(), entry.id(), cmd.productId(), cmd.warehouseId(),
                            MovementType.ENTRY_PURCHASE, cmd.quantity(), cmd.reference(),
                            cmd.notes(), cmd.performedBy());
                    return stockEntryRepository.save(updated)
                            .then(movementRepository.save(movement));
                })
                .then();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Locks the requested quantity for a future dispatch. Fails if insufficient
     * available stock (total − already reserved).</p>
     */
    @Override
    public Mono<Void> reserveStock(ReserveStockCommand cmd) {
        return stockEntryRepository.findByProductAndWarehouse(
                        cmd.tenantId(), cmd.productId(), cmd.warehouseId())
                .switchIfEmpty(Mono.error(new StockEntryNotFoundException(
                        cmd.productId(), cmd.warehouseId())))
                .flatMap(entry -> {
                    StockEntry updated = entry.reserve(cmd.quantity());
                    InventoryMovement movement = InventoryMovement.record(
                            cmd.tenantId(), entry.id(), cmd.productId(), cmd.warehouseId(),
                            MovementType.RESERVED, cmd.quantity(), cmd.reference(), null, null);
                    return stockEntryRepository.save(updated)
                            .then(movementRepository.save(movement));
                })
                .then();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> releaseStock(UUID tenantId, UUID productId, UUID warehouseId,
                                    double quantity, String reference) {
        return stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId)
                .switchIfEmpty(Mono.error(new StockEntryNotFoundException(productId, warehouseId)))
                .flatMap(entry -> {
                    StockEntry updated = entry.releaseReservation(quantity);
                    InventoryMovement movement = InventoryMovement.record(
                            tenantId, entry.id(), productId, warehouseId,
                            MovementType.RESERVATION_RELEASED, quantity, reference, null, null);
                    return stockEntryRepository.save(updated)
                            .then(movementRepository.save(movement));
                })
                .then();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> consumeStock(UUID tenantId, UUID productId, UUID warehouseId,
                                    double quantity, String reference, UUID performedBy) {
        return stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId)
                .switchIfEmpty(Mono.error(new StockEntryNotFoundException(productId, warehouseId)))
                .flatMap(entry -> {
                    StockEntry updated = entry.consume(quantity);
                    InventoryMovement movement = InventoryMovement.record(
                            tenantId, entry.id(), productId, warehouseId,
                            MovementType.EXIT_SALE, quantity, reference, null, performedBy);
                    return stockEntryRepository.save(updated)
                            .then(movementRepository.save(movement))
                            .then(checkAndTriggerAlert(updated));
                })
                .then();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<StockEntry> getStockEntry(UUID tenantId, UUID productId, UUID warehouseId) {
        return stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId)
                .switchIfEmpty(Mono.error(new StockEntryNotFoundException(productId, warehouseId)));
    }

    /** {@inheritDoc} */
    @Override
    public Flux<InventoryAlert> getLowStockAlerts(UUID tenantId) {
        return alertRepository.findUnacknowledgedByTenant(tenantId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Creates a new stock entry for the given product/warehouse combination.
     * Optionally resolves a Kernel stock entry link before persisting.
     *
     * <p>The Kernel lookup is best-effort — any failure returns an unlinked entry.</p>
     *
     * @param cmd the receive stock command carrying tenant, product, and warehouse IDs
     * @return a Mono emitting the newly created (and possibly Kernel-linked) StockEntry
     */
    private Mono<StockEntry> resolveOrCreateStockEntry(ReceiveStockCommand cmd) {
        StockEntry base = StockEntry.create(cmd.tenantId(), cmd.productId(),
                cmd.warehouseId(), "UNIT", null);
        return kernelInventoryPort
                .findByProductAndWarehouse(cmd.productId(), cmd.warehouseId(), cmd.tenantId())
                .map(kernelDto -> {
                    log.debug("Linked TNT stock entry to Kernel stockEntryId={}",
                            kernelDto.kernelStockEntryId());
                    return base.withKernelStockEntryId(kernelDto.kernelStockEntryId());
                })
                .defaultIfEmpty(base); // Kernel link is optional — proceed without it
    }

    /**
     * Checks if stock has dropped below the reorder threshold and triggers
     * a persistent alert and a Kafka event if so.
     *
     * @param entry the updated stock entry after a consume operation
     * @return Mono completing after alert persistence and event publication
     */
    private Mono<Void> checkAndTriggerAlert(StockEntry entry) {
        if (!entry.needsReorder()) return Mono.empty();
        AlertType alertType = entry.isOutOfStock() ? AlertType.OUT_OF_STOCK : AlertType.LOW_STOCK;
        InventoryAlert alert = InventoryAlert.trigger(
                entry.tenantId(), entry.productId(), entry.warehouseId(),
                alertType, entry.availableQuantity(), entry.reorderThreshold());
        return alertRepository.save(alert)
                .then(eventPublisher.publishStockLow(StockLowEvent.of(
                        entry.productId(), entry.warehouseId(), entry.tenantId(),
                        entry.availableQuantity(), entry.reorderThreshold())));
    }
}
