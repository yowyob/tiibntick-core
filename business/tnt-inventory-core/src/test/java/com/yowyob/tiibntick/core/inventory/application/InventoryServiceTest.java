package com.yowyob.tiibntick.core.inventory.application;

import com.yowyob.tiibntick.core.inventory.application.port.in.ReceiveStockCommand;
import com.yowyob.tiibntick.core.inventory.application.port.in.ReserveStockCommand;
import com.yowyob.tiibntick.core.inventory.application.port.out.*;
import com.yowyob.tiibntick.core.inventory.application.service.InventoryApplicationService;
import com.yowyob.tiibntick.core.inventory.domain.exception.StockEntryNotFoundException;
import com.yowyob.tiibntick.core.inventory.domain.model.KernelStockEntryDto;
import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryApplicationService}.
 *
 * <p>Verifies stock operations (receive, reserve, release, consume) and the
 * optional Kernel stock entry resolution via {@link KernelInventoryPort}.</p>
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private StockEntryRepository stockEntryRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private InventoryAlertRepository alertRepository;
    @Mock private InventoryEventPublisherPort eventPublisher;
    @Mock private KernelInventoryPort kernelInventoryPort;

    @InjectMocks
    private InventoryApplicationService service;

    @Test
    void shouldReceiveStockAndCreateEntryWhenNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId))
                .thenReturn(Mono.empty());
        // Kernel has no matching entry — optional link returns empty
        when(kernelInventoryPort.findByProductAndWarehouse(productId, warehouseId, tenantId))
                .thenReturn(Mono.empty());
        when(stockEntryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReceiveStockCommand cmd = new ReceiveStockCommand(tenantId, productId, warehouseId,
                50.0, "PO-001", "Initial stock", null);

        StepVerifier.create(service.receiveStock(cmd)).verifyComplete();
    }

    @Test
    void shouldReceiveStockAndLinkKernelEntryWhenFound() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID kernelId = UUID.randomUUID();

        KernelStockEntryDto kernelDto = new KernelStockEntryDto(
                kernelId, productId, warehouseId, 0.0, "UNIT", tenantId, true);

        when(stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId))
                .thenReturn(Mono.empty());
        // Kernel has a matching entry — link it
        when(kernelInventoryPort.findByProductAndWarehouse(productId, warehouseId, tenantId))
                .thenReturn(Mono.just(kernelDto));
        when(stockEntryRepository.save(any())).thenAnswer(inv -> {
            StockEntry saved = inv.getArgument(0);
            // Verify the Kernel link was resolved before saving
            assertThat(saved.kernelStockEntryId()).isEqualTo(kernelId);
            return Mono.just(saved);
        });
        when(movementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReceiveStockCommand cmd = new ReceiveStockCommand(tenantId, productId, warehouseId,
                50.0, "PO-002", "Kernel-linked stock", null);

        StepVerifier.create(service.receiveStock(cmd)).verifyComplete();
    }

    @Test
    void shouldReserveStockSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        StockEntry existingEntry = StockEntry.create(tenantId, productId, warehouseId, "UNIT", null)
                .receive(100.0);

        when(stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId))
                .thenReturn(Mono.just(existingEntry));
        when(stockEntryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReserveStockCommand cmd = new ReserveStockCommand(tenantId, productId, warehouseId,
                30.0, "ORDER-001");

        StepVerifier.create(service.reserveStock(cmd)).verifyComplete();
    }

    @Test
    void shouldFailReservationWhenStockNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId))
                .thenReturn(Mono.empty());

        ReserveStockCommand cmd = new ReserveStockCommand(tenantId, productId, warehouseId,
                10.0, "ORDER-002");

        StepVerifier.create(service.reserveStock(cmd))
                .expectError(StockEntryNotFoundException.class)
                .verify();
    }

    @Test
    void shouldGetStockEntry() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        StockEntry entry = StockEntry.create(tenantId, productId, warehouseId, "KG", 5.0)
                .receive(50.0);

        when(stockEntryRepository.findByProductAndWarehouse(tenantId, productId, warehouseId))
                .thenReturn(Mono.just(entry));

        StepVerifier.create(service.getStockEntry(tenantId, productId, warehouseId))
                .assertNext(e -> {
                    assertThat(e.quantity()).isEqualTo(50.0);
                    assertThat(e.availableQuantity()).isEqualTo(50.0);
                })
                .verifyComplete();
    }
}
