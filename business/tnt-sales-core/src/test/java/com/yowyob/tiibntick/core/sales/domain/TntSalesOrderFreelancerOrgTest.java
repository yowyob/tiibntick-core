package com.yowyob.tiibntick.core.sales.domain;

import com.yowyob.tiibntick.core.sales.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to TntSalesOrder.
 *
 * @author MANFOUO Braun
 */
class TntSalesOrderFreelancerOrgTest {

    private TntSalesOrder baseOrder;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TntAddress addr = new TntAddress("Rue Melen", "Centre", "Yaoundé", "CM", null, null, null, "Client", "+237600000001");
        baseOrder = TntSalesOrder.rehydrate(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TNT-SO-001",
                List.of(), addr, null,
                SalesOrderStatus.CONFIRMED, OrderPriority.NORMAL, PaymentStatus.PENDING,
                "XAF", new BigDecimal("5000"), new BigDecimal("5000"),
                null, null, null,
                null, null,
                null, null, null,
                Instant.now(), null, null, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("providerOrgType and providerOrgId defaults")
    class Defaults {

        @Test
        @DisplayName("New order via rehydrateFull with null provider should have null context")
        void nullProviderContextIsAllowed() {
            assertThat(baseOrder.getProviderOrgType()).isNull();
            assertThat(baseOrder.getProviderOrgId()).isNull();
            assertThat(baseOrder.isFreelancerOrgOrder()).isFalse();
        }
    }

    @Nested
    @DisplayName("withProviderOrg")
    class WithProviderOrg {

        @Test
        @DisplayName("Should set FREELANCER_ORG provider context")
        void shouldSetFreelancerOrgContext() {
            String orgId = "FRL-ORG-001";
            TntSalesOrder withOrg = baseOrder.withProviderOrg("FREELANCER_ORG", orgId);

            assertThat(withOrg.getProviderOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(withOrg.getProviderOrgId()).isEqualTo(orgId);
            assertThat(withOrg.isFreelancerOrgOrder()).isTrue();
        }

        @Test
        @DisplayName("withProviderOrg should be immutable — original unchanged")
        void immutableUpdate() {
            baseOrder.withProviderOrg("FREELANCER_ORG", "FRL-ORG-001");
            assertThat(baseOrder.getProviderOrgType()).isNull();
        }

        @Test
        @DisplayName("AGENCY provider context should not be isFreelancerOrgOrder")
        void agencyProviderIsNotFreelancerOrg() {
            TntSalesOrder withAgency = baseOrder.withProviderOrg("AGENCY", UUID.randomUUID().toString());
            assertThat(withAgency.isFreelancerOrgOrder()).isFalse();
        }
    }

    @Nested
    @DisplayName("rehydrateFull backward compat")
    class RehydrateCompatibility {

        @Test
        @DisplayName("Legacy rehydrate() should default to null provider context")
        void legacyRehydrateHasNullProvider() {
            TntAddress addr = new TntAddress("Rue Test", null, "Douala", "CM", null, null, null, "Test", "+237600000000");
            TntSalesOrder order = TntSalesOrder.rehydrate(
                    UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), "TNT-SO-LEGACY", List.of(), addr, null,
                    SalesOrderStatus.DRAFT, OrderPriority.NORMAL, PaymentStatus.PENDING,
                    "XAF", BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, null, null, null, null,
                    null, null, Instant.now(), null, null, Instant.now(), Instant.now());

            assertThat(order.getProviderOrgType()).isNull();
            assertThat(order.getProviderOrgId()).isNull();
        }
    }
}
