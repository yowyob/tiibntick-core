package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceCancelled;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceGenerated;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoicePaid;
import com.yowyob.tiibntick.core.billing.invoice.domain.exception.InvoiceStateException;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Invoice aggregate root.
 *
 * @author MANFOUO Braun
 */
class InvoiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String TENANT_CODE = "AGY001";

    private InvoiceNumber invoiceNumber;
    private InvoiceLine line;

    @BeforeEach
    void setUp() {
        invoiceNumber = InvoiceNumber.generate(TENANT_CODE, 2026, 42L);
        Money unitPrice = Money.xaf(10_000);
        line = InvoiceLine.of(1, "Delivery fee", 1.0, unitPrice, BigDecimal.ZERO, LineItemType.DELIVERY_FEE);
    }

    @Test
    void create_shouldBuildInvoiceWithCorrectTotals() {
        Invoice invoice = Invoice.create(
                invoiceNumber, TENANT_ID, TENANT_CODE, "CM",
                "MISSION-001", null, "CLIENT-001",
                List.of(line), List.of(), "XAF");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getSubtotalExTax()).isEqualTo(Money.xaf(10_000));
        assertThat(invoice.getDomainEvents()).isEmpty();
    }

    @Test
    void issue_shouldTransitionToIssuedAndEmitEvent() {
        Invoice draft = Invoice.create(
                invoiceNumber, TENANT_ID, TENANT_CODE, "CM",
                "MISSION-001", null, "CLIENT-001",
                List.of(line), List.of(), "XAF");

        Invoice issued = draft.issue(LocalDateTime.now().plusDays(7));

        assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issued.getIssuedAt()).isNotNull();
        assertThat(issued.getDomainEvents()).hasSize(1);
        assertThat(issued.getDomainEvents().get(0)).isInstanceOf(InvoiceGenerated.class);
    }

    @Test
    void markPaid_shouldTransitionToPaydAndEmitEvent() {
        Invoice issued = Invoice.create(
                invoiceNumber, TENANT_ID, TENANT_CODE, "CM",
                "MISSION-001", null, "CLIENT-001",
                List.of(line), List.of(), "XAF")
                .issue(LocalDateTime.now().plusDays(7));

        Invoice paid = issued.markPaid("MOMO-REF-12345");

        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getDomainEvents()).hasSize(1);
        assertThat(paid.getDomainEvents().get(0)).isInstanceOf(InvoicePaid.class);
    }

    @Test
    void cancel_shouldTransitionToCancelledAndEmitEvent() {
        Invoice issued = Invoice.create(
                invoiceNumber, TENANT_ID, TENANT_CODE, "CM",
                "MISSION-001", null, "CLIENT-001",
                List.of(line), List.of(), "XAF")
                .issue(LocalDateTime.now().plusDays(7));

        Invoice cancelled = issued.cancel("Client requested cancellation");

        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Client requested cancellation");
        assertThat(cancelled.getDomainEvents()).hasSize(1);
        assertThat(cancelled.getDomainEvents().get(0)).isInstanceOf(InvoiceCancelled.class);
    }

    @Test
    void cancel_paidInvoice_shouldThrowInvoiceStateException() {
        Invoice paid = Invoice.create(
                invoiceNumber, TENANT_ID, TENANT_CODE, "CM",
                "MISSION-001", null, "CLIENT-001",
                List.of(line), List.of(), "XAF")
                .issue(LocalDateTime.now().plusDays(7))
                .markPaid("MOMO-REF-99999");

        assertThatThrownBy(() -> paid.cancel("Try to cancel"))
                .isInstanceOf(InvoiceStateException.class);
    }

    @Test
    void taxLine_cameroon_shouldApply19_25Percent() {
        Money base = Money.xaf(100_000);
        TaxLine tva = TaxLine.vatCameroon(base);

        assertThat(tva.ratePercent()).isEqualByComparingTo(new BigDecimal("19.25"));
        assertThat(tva.taxAmount().amount()).isEqualByComparingTo(new BigDecimal("19250.00"));
        assertThat(tva.countryCode()).isEqualTo("CM");
    }

    @Test
    void taxLine_nigeria_shouldApply7_5Percent() {
        Money base = Money.ngn(new BigDecimal("100000"));
        TaxLine vat = TaxLine.vatNigeria(base);

        assertThat(vat.ratePercent()).isEqualByComparingTo(new BigDecimal("7.5"));
        assertThat(vat.taxAmount().amount()).isEqualByComparingTo(new BigDecimal("7500.00"));
    }

    @Test
    void money_operations_shouldBeCorrect() {
        Money a = Money.xaf(10_000);
        Money b = Money.xaf(5_000);

        assertThat(a.add(b)).isEqualTo(Money.xaf(15_000));
        assertThat(a.subtract(b)).isEqualTo(Money.xaf(5_000));
        assertThat(a.percentage(new BigDecimal("19.25")).amount())
                .isEqualByComparingTo(new BigDecimal("1925.00"));
    }

    @Test
    void invoiceNumber_shouldFollowFormat() {
        InvoiceNumber number = InvoiceNumber.generate("AGY001", 2026, 42L);
        assertThat(number.value()).isEqualTo("TNT-FACT-AGY001-2026-000042");
    }

    @Test
    void invoiceNumber_withInvalidFormat_shouldThrow() {
        assertThatThrownBy(() -> new InvoiceNumber("INVALID-FORMAT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
