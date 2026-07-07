package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to the Invoice domain model.
 *
 * @author MANFOUO Braun
 */
class InvoiceFreelancerOrgTest {

    @Nested
    @DisplayName("SurchargeLineItem")
    class SurchargeLineItemTests {

        @Test
        @DisplayName("fixed() creates XAF unit surcharge")
        void fixedSurcharge() {
            SurchargeLineItem item = SurchargeLineItem.fixed(
                    "FRAGILE", "Majoration fragile", "Fragile surcharge", new BigDecimal("500"));
            assertThat(item.surchargeCode()).isEqualTo("FRAGILE");
            assertThat(item.unit()).isEqualTo("XAF");
            assertThat(item.isDiscount()).isFalse();
        }

        @Test
        @DisplayName("percentage() creates % unit surcharge")
        void percentageSurcharge() {
            SurchargeLineItem item = SurchargeLineItem.percentage(
                    "LOYALTY", "Remise fidélité", "Loyalty discount", new BigDecimal("-50"));
            assertThat(item.unit()).isEqualTo("%");
            assertThat(item.isDiscount()).isTrue();
        }

        @Test
        @DisplayName("defaults unit to XAF when null")
        void defaultsUnitToXAF() {
            SurchargeLineItem item = new SurchargeLineItem("TEST", "Test", "Test", BigDecimal.TEN, null);
            assertThat(item.unit()).isEqualTo("XAF");
        }
    }

    @Nested
    @DisplayName("LineItemType.SURCHARGE")
    class LineItemTypeSurcharge {

        @Test
        @DisplayName("SURCHARGE value must exist in LineItemType enum")
        void surchargeValueExists() {
            assertThat(LineItemType.valueOf("SURCHARGE")).isEqualTo(LineItemType.SURCHARGE);
        }
    }

    @Nested
    @DisplayName("Invoice.createWithContext")
    class CreateWithContext {

        @Test
        @DisplayName("Should carry FreelancerOrg context through issue lifecycle")
        void shouldCarryFreelancerContext() {
            UUID tenantId = UUID.randomUUID();
            InvoiceLine line = InvoiceLine.create(
                    "Livraison Yaoundé Centre", LineItemType.DELIVERY_FEE,
                    new Money(new BigDecimal("2000"), "XAF"), 1, null);

            SurchargeLineItem surcharge = SurchargeLineItem.fixed(
                    "FRAGILE", "Majoration fragile", "Fragile surcharge", new BigDecimal("300"));

            Invoice draft = Invoice.createWithContext(
                    new InvoiceNumber("TNT-FACT-TEST-2026-001"),
                    tenantId, "TEST", "CM",
                    "MISSION-001", null, "CLIENT-001",
                    List.of(line), List.of(), "XAF",
                    "FREELANCER_ORG", "FRL-ORG-123", "Moto Express Biyem",
                    false, List.of(surcharge), true, "TPL-FRAGILE");

            assertThat(draft.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(draft.getIssuerOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(draft.getIssuerOrgId()).isEqualTo("FRL-ORG-123");
            assertThat(draft.getIssuerTradeName()).isEqualTo("Moto Express Biyem");
            assertThat(draft.getVatApplicable()).isFalse();
            assertThat(draft.getSurchargeLines()).hasSize(1);
            assertThat(draft.getSurchargeLines().get(0).surchargeCode()).isEqualTo("FRAGILE");
            assertThat(draft.getIsFromTemplate()).isTrue();
            assertThat(draft.getAppliedTemplateName()).isEqualTo("TPL-FRAGILE");

            // Issue and verify fields propagate
            Invoice issued = draft.issue(java.time.LocalDateTime.now().plusDays(7));
            assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
            assertThat(issued.getIssuerTradeName()).isEqualTo("Moto Express Biyem");
            assertThat(issued.getSurchargeLines()).hasSize(1);
        }

        @Test
        @DisplayName("Standard create() should have null FreelancerOrg context")
        void standardCreateHasNullContext() {
            UUID tenantId = UUID.randomUUID();
            InvoiceLine line = InvoiceLine.create("Test", LineItemType.DELIVERY_FEE,
                    new Money(new BigDecimal("1000"), "XAF"), 1, null);

            Invoice draft = Invoice.create(
                    new InvoiceNumber("TNT-FACT-TEST-2026-002"),
                    tenantId, "TEST", "CM",
                    "MISSION-002", null, "CLIENT-002",
                    List.of(line), List.of(), "XAF");

            assertThat(draft.getIssuerOrgType()).isNull();
            assertThat(draft.getIssuerOrgId()).isNull();
            assertThat(draft.getSurchargeLines()).isEmpty();
            assertThat(draft.getIsFromTemplate()).isNull();
        }
    }
}
