package com.yowyob.tiibntick.core.inventory.domain;

import com.yowyob.tiibntick.core.inventory.domain.exception.InsufficientStockException;
import com.yowyob.tiibntick.core.inventory.domain.model.StockEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link StockEntry} aggregate root.
 *
 * <p>Tests cover all quantity invariants and the optional Kernel stock entry link
 * ({@code kernelStockEntryId}) introduced in the Kernel extension refactoring.</p>
 *
 * @author MANFOUO Braun
 */
class StockEntryTest {

    private UUID tenantId;
    private UUID productId;
    private UUID warehouseId;
    private StockEntry emptyEntry;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        // No Kernel link — informal stock
        emptyEntry = StockEntry.create(tenantId, productId, warehouseId, "UNIT", 10.0);
    }

    @Test
    void shouldCreateEmptyStock() {
        assertThat(emptyEntry.quantity()).isEqualTo(0.0);
        assertThat(emptyEntry.reservedQuantity()).isEqualTo(0.0);
        assertThat(emptyEntry.availableQuantity()).isEqualTo(0.0);
        assertThat(emptyEntry.isOutOfStock()).isTrue();
        // No Kernel link by default
        assertThat(emptyEntry.kernelStockEntryId()).isNull();
        assertThat(emptyEntry.hasKernelLink()).isFalse();
    }

    @Test
    void shouldCreateStockWithKernelLink() {
        UUID kernelId = UUID.randomUUID();
        StockEntry linked = StockEntry.create(tenantId, productId, warehouseId, "KG", 5.0, kernelId);
        assertThat(linked.kernelStockEntryId()).isEqualTo(kernelId);
        assertThat(linked.hasKernelLink()).isTrue();
    }

    @Test
    void shouldLinkKernelStockEntryAfterCreation() {
        UUID kernelId = UUID.randomUUID();
        StockEntry linked = emptyEntry.withKernelStockEntryId(kernelId);
        assertThat(linked.kernelStockEntryId()).isEqualTo(kernelId);
        // Original entry is unchanged (immutable)
        assertThat(emptyEntry.kernelStockEntryId()).isNull();
    }

    @Test
    void shouldReceiveStock() {
        StockEntry updated = emptyEntry.receive(50.0);
        assertThat(updated.quantity()).isEqualTo(50.0);
        assertThat(updated.availableQuantity()).isEqualTo(50.0);
    }

    @Test
    void shouldReserveStock() {
        StockEntry withStock = emptyEntry.receive(100.0);
        StockEntry reserved = withStock.reserve(30.0);
        assertThat(reserved.reservedQuantity()).isEqualTo(30.0);
        assertThat(reserved.availableQuantity()).isEqualTo(70.0);
        assertThat(reserved.quantity()).isEqualTo(100.0);
    }

    @Test
    void shouldReleaseReservation() {
        StockEntry withStock = emptyEntry.receive(100.0).reserve(30.0);
        StockEntry released = withStock.releaseReservation(30.0);
        assertThat(released.reservedQuantity()).isEqualTo(0.0);
        assertThat(released.availableQuantity()).isEqualTo(100.0);
    }

    @Test
    void shouldConsumeStock() {
        StockEntry withStock = emptyEntry.receive(100.0);
        StockEntry consumed = withStock.consume(40.0);
        assertThat(consumed.quantity()).isEqualTo(60.0);
        assertThat(consumed.availableQuantity()).isEqualTo(60.0);
    }

    @Test
    void shouldFailReservationExceedingAvailable() {
        StockEntry withStock = emptyEntry.receive(20.0);
        assertThatThrownBy(() -> withStock.reserve(30.0))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldFailConsumeExceedingAvailable() {
        StockEntry withStock = emptyEntry.receive(20.0).reserve(15.0);
        // Available is 5.0; consuming 10.0 should fail
        assertThatThrownBy(() -> withStock.consume(10.0))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void shouldDetectReorderNeeded() {
        StockEntry lowStock = emptyEntry.receive(8.0); // reorderThreshold=10.0
        assertThat(lowStock.needsReorder()).isTrue();
    }

    @Test
    void shouldDispatchReservedStock() {
        StockEntry withStock = emptyEntry.receive(100.0).reserve(30.0);
        StockEntry dispatched = withStock.dispatchReserved(30.0);
        assertThat(dispatched.quantity()).isEqualTo(70.0);
        assertThat(dispatched.reservedQuantity()).isEqualTo(0.0);
    }

    @Test
    void shouldPreserveKernelLinkThroughMutations() {
        // Ensure the optional Kernel link is preserved through all domain mutations
        UUID kernelId = UUID.randomUUID();
        StockEntry linked = StockEntry.create(tenantId, productId, warehouseId, "UNIT", null, kernelId);
        StockEntry afterReceive = linked.receive(100.0);
        StockEntry afterReserve = afterReceive.reserve(20.0);
        StockEntry afterConsume = afterReserve.consume(10.0);

        assertThat(afterReceive.kernelStockEntryId()).isEqualTo(kernelId);
        assertThat(afterReserve.kernelStockEntryId()).isEqualTo(kernelId);
        assertThat(afterConsume.kernelStockEntryId()).isEqualTo(kernelId);
    }
}
