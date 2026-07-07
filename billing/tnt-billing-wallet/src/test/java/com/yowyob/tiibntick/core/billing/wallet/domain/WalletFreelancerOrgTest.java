package com.yowyob.tiibntick.core.billing.wallet.domain;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletOwnerType;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to the Wallet domain model.
 *
 * @author MANFOUO Braun
 */
class WalletFreelancerOrgTest {

    @Nested
    @DisplayName("Wallet.createNew")
    class CreateNew {
        @Test
        @DisplayName("Should default ownerType to ACTOR")
        void defaultOwnerTypeIsActor() {
            UUID userId = UUID.randomUUID();
            Wallet wallet = Wallet.createNew(userId, UUID.randomUUID(), Currency.getInstance("XAF"));
            assertThat(wallet.getOwnerType()).isEqualTo(WalletOwnerType.ACTOR);
            assertThat(wallet.getOwnerId()).isEqualTo(userId.toString());
            assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Wallet.createForOrg")
    class CreateForOrg {

        @Test
        @DisplayName("Should create FREELANCER_ORG wallet with correct ownerType")
        void createFreelancerOrgWallet() {
            String orgId = "FRL-ORG-" + UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            Wallet orgWallet = Wallet.createForOrg(WalletOwnerType.FREELANCER_ORG, orgId,
                    tenantId, Currency.getInstance("XAF"));

            assertThat(orgWallet.getOwnerType()).isEqualTo(WalletOwnerType.FREELANCER_ORG);
            assertThat(orgWallet.getOwnerId()).isEqualTo(orgId);
            assertThat(orgWallet.getUserId()).isNull();
            assertThat(orgWallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(orgWallet.availableBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should reject ACTOR type in createForOrg")
        void rejectActorType() {
            assertThatThrownBy(() ->
                    Wallet.createForOrg(WalletOwnerType.ACTOR, "user-1", UUID.randomUUID(),
                            Currency.getInstance("XAF")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("createNew");
        }
    }

    @Nested
    @DisplayName("PaymentSplit")
    class PaymentSplitTests {

        @Test
        @DisplayName("Should compute split amounts correctly")
        void computeSplitCorrectly() {
            BigDecimal total = new BigDecimal("10000");
            PaymentSplit split = PaymentSplit.createForFreelancerOrg(
                    "MISSION-001", total, 0.05, "FRL-ORG-1", "SUB-001", 0.10);

            // platform = 10000 * 0.05 = 500
            assertThat(split.getPlatformCommission()).isEqualByComparingTo("500.00");
            // sub = 10000 * 0.10 = 1000
            assertThat(split.getSubDelivererCommission()).isEqualByComparingTo("1000.00");
            // org = 10000 - 500 - 1000 = 8500
            assertThat(split.getOrgRevenue()).isEqualByComparingTo("8500.00");
            assertThat(split.getMissionId()).isEqualTo("MISSION-001");
        }

        @Test
        @DisplayName("Should have zero sub-deliverer commission when null subDelivererId")
        void nullSubDelivererGivesZeroCommission() {
            PaymentSplit split = PaymentSplit.createForFreelancerOrg(
                    "MISSION-002", new BigDecimal("5000"), 0.05, "FRL-ORG-1", null, 0.10);
            assertThat(split.getSubDelivererCommission()).isNull();
            assertThat(split.getSubDelivererId()).isNull();
            // org = 5000 - 250 = 4750
            assertThat(split.getOrgRevenue()).isEqualByComparingTo("4750.00");
        }

        @Test
        @DisplayName("markExecuted should set CONFIRMED status")
        void markExecuted() {
            PaymentSplit split = PaymentSplit.createForFreelancerOrg(
                    "MISSION-003", new BigDecimal("3000"), 0.05, "FRL-ORG-1", null, 0.0);
            split.markExecuted();
            assertThat(split.getStatus().name()).isEqualTo("CONFIRMED");
            assertThat(split.getExecutedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("PaymentSplitResult")
    class PaymentSplitResultTests {

        @Test
        @DisplayName("fromSplit should map all fields")
        void fromSplitMapsAllFields() {
            PaymentSplit split = PaymentSplit.createForFreelancerOrg(
                    "M-001", new BigDecimal("10000"), 0.05, "FRL-001", "SUB-001", 0.10);
            PaymentSplitResult result = PaymentSplitResult.fromSplit(split);

            assertThat(result.missionId()).isEqualTo("M-001");
            assertThat(result.totalAmount()).isEqualByComparingTo("10000");
            assertThat(result.currency()).isEqualTo("XAF");
            assertThat(result.platformCommission()).isEqualByComparingTo("500.00");
            assertThat(result.orgRevenue()).isEqualByComparingTo("8500.00");
            assertThat(result.subDelivererCommission()).isEqualByComparingTo("1000.00");
        }
    }
}
