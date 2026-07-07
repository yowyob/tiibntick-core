package com.yowyob.tiibntick.core.sales.domain;

import com.yowyob.tiibntick.core.sales.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link TntSalesOrder} aggregate root.
 *
 * <p>Covers all state transitions, invariant enforcement, and the optional
 * Kernel sales order link ({@code kernelSalesOrderId}) introduced in the
 * Kernel extension refactoring.</p>
 *
 * @author MANFOUO Braun
 */
class TntSalesOrderTest {

    private TntSalesOrder buildOrder() {
        return buildOrder(null);
    }

    private TntSalesOrder buildOrder(UUID kernelSalesOrderId) {
        TntAddress addr = TntAddress.of("Bastos", "Yaoundé", "Cameroon",
                "Pharmacie Bastos", "Jean Dupont", "+237699000001");
        List<TntSalesOrderLine> lines = List.of(
                TntSalesOrderLine.create(UUID.randomUUID(), "Colis 1kg", "SKU-001",
                        BigDecimal.ONE, new BigDecimal("5000"), "XAF"));
        return TntSalesOrder.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TNT-ORD-YDE-2026-000001", lines, addr, null,
                OrderPriority.NORMAL, "XAF", kernelSalesOrderId);
    }

    @Test
    void should_create_order_in_draft_status_no_kernel_link() {
        TntSalesOrder order = buildOrder();
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        // No Kernel link by default
        assertThat(order.getKernelSalesOrderId()).isNull();
        assertThat(order.hasKernelLink()).isFalse();
    }

    @Test
    void should_create_order_with_kernel_link() {
        UUID kernelId = UUID.randomUUID();
        TntSalesOrder order = buildOrder(kernelId);
        assertThat(order.getKernelSalesOrderId()).isEqualTo(kernelId);
        assertThat(order.hasKernelLink()).isTrue();
    }

    @Test
    void should_link_kernel_sales_order_after_creation() {
        UUID kernelId = UUID.randomUUID();
        TntSalesOrder linked = buildOrder().withKernelSalesOrderId(kernelId);
        assertThat(linked.getKernelSalesOrderId()).isEqualTo(kernelId);
        // Original is immutable
        assertThat(buildOrder().getKernelSalesOrderId()).isNull();
    }

    @Test
    void should_preserve_kernel_link_through_all_transitions() {
        UUID kernelId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        TntSalesOrder delivered = buildOrder(kernelId)
                .confirm().reserveStock().dispatch(missionId).startDelivery().markDelivered();

        // Kernel link must be preserved across all mutations
        assertThat(delivered.getKernelSalesOrderId()).isEqualTo(kernelId);
    }

    @Test
    void should_transition_draft_to_confirmed() {
        TntSalesOrder confirmed = buildOrder().confirm();
        assertThat(confirmed.getStatus()).isEqualTo(SalesOrderStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
    }

    @Test
    void should_transition_confirmed_to_stock_reserved() {
        TntSalesOrder reserved = buildOrder().confirm().reserveStock();
        assertThat(reserved.getStatus()).isEqualTo(SalesOrderStatus.STOCK_RESERVED);
    }

    @Test
    void should_dispatch_with_mission_id() {
        UUID missionId = UUID.randomUUID();
        TntSalesOrder dispatched = buildOrder().confirm().reserveStock().dispatch(missionId);
        assertThat(dispatched.getStatus()).isEqualTo(SalesOrderStatus.DISPATCHED);
        assertThat(dispatched.getMissionId()).isEqualTo(missionId);
    }

    @Test
    void should_mark_as_delivered() {
        UUID missionId = UUID.randomUUID();
        TntSalesOrder delivered = buildOrder().confirm().reserveStock()
                .dispatch(missionId).startDelivery().markDelivered();
        assertThat(delivered.getStatus()).isEqualTo(SalesOrderStatus.DELIVERED);
        assertThat(delivered.getDeliveredAt()).isNotNull();
    }

    @Test
    void should_not_cancel_delivered_order() {
        UUID missionId = UUID.randomUUID();
        TntSalesOrder delivered = buildOrder().confirm().reserveStock()
                .dispatch(missionId).startDelivery().markDelivered();
        assertThatThrownBy(() -> delivered.cancel("changed mind"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_return_dispatched_order() {
        UUID missionId = UUID.randomUUID();
        TntSalesOrder returned = buildOrder().confirm().reserveStock().dispatch(missionId)
                .markReturned(ReturnReason.RECIPIENT_ABSENT, "Personne au domicile");
        assertThat(returned.getStatus()).isEqualTo(SalesOrderStatus.RETURNED);
        assertThat(returned.getReturnReason()).isEqualTo(ReturnReason.RECIPIENT_ABSENT);
    }

    @Test
    void should_fail_confirm_from_non_draft() {
        TntSalesOrder confirmed = buildOrder().confirm();
        assertThatThrownBy(confirmed::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void should_link_invoice() {
        UUID invoiceId = UUID.randomUUID();
        TntSalesOrder withInvoice = buildOrder().confirm().linkInvoice(invoiceId);
        assertThat(withInvoice.getInvoiceId()).isEqualTo(invoiceId);
    }
}
