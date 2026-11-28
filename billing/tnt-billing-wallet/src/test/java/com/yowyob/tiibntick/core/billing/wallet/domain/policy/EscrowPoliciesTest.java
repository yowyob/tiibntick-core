package com.yowyob.tiibntick.core.billing.wallet.domain.policy;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy.Action;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy.DeliveryEnd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Lot L5: split of an escrowed price and outcome of the escrow (spec §4.1, §4.2)")
class EscrowPoliciesTest {

    @Test
    @DisplayName("PLATFORM = price × rate rounded half-up to the franc, MERCHANT as given (QO-19), COURIER = the rest")
    void split() {
        EscrowShares s = EscrowSharesPolicy.split(new BigDecimal("3000"), new BigDecimal("0.05"), List.of());
        assertThat(s.platform()).isEqualByComparingTo("150");
        assertThat(s.courier()).isEqualByComparingTo("2850");

        EscrowShares odd = EscrowSharesPolicy.split(new BigDecimal("1010"), new BigDecimal("0.05"), List.of());
        assertThat(odd.platform()).isEqualByComparingTo("51"); // 50.5 → 51
        assertThat(odd.courier()).isEqualByComparingTo("959");

        UUID relay = UUID.randomUUID();
        EscrowShares withMerchant = EscrowSharesPolicy.split(new BigDecimal("3000"), new BigDecimal("0.05"),
                List.of(new EscrowShares.Part(relay, new BigDecimal("500"))));
        assertThat(withMerchant.merchantTotal()).isEqualByComparingTo("500");
        assertThat(withMerchant.courier()).isEqualByComparingTo("2350");
        assertThat(withMerchant.platform().add(withMerchant.merchantTotal()).add(withMerchant.courier()))
                .isEqualByComparingTo(withMerchant.price());
    }

    @Test
    @DisplayName("refused: non-positive price, rate outside [0, 1], parts above the price")
    void splitRefusals() {
        assertThatThrownBy(() -> EscrowSharesPolicy.split(BigDecimal.ZERO, new BigDecimal("0.05"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EscrowSharesPolicy.split(BigDecimal.TEN, new BigDecimal("1.5"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EscrowSharesPolicy.split(new BigDecimal("100"), new BigDecimal("0.05"),
                List.of(new EscrowShares.Part(UUID.randomUUID(), new BigDecimal("99")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("delivery ends: released when delivered or deposited at its destination relay (QO-10); refunded before pickup or on loss; by fault otherwise (QO-24)")
    void deliveryEnds() {
        assertThat(EscrowOutcomePolicy.onDeliveryEnd(DeliveryEnd.DELIVERED, null)).isEqualTo(Action.RELEASE);
        assertThat(EscrowOutcomePolicy.onDeliveryEnd(DeliveryEnd.DEPOSITED_AT_DESTINATION_RELAY, null))
                .isEqualTo(Action.RELEASE);
        assertThat(EscrowOutcomePolicy.onDeliveryEnd(DeliveryEnd.CANCELLED_BEFORE_PICKUP, "COURIER"))
                .isEqualTo(Action.REFUND);
        assertThat(EscrowOutcomePolicy.onDeliveryEnd(DeliveryEnd.PARCEL_LOST_OR_DAMAGED, "SENDER"))
                .isEqualTo(Action.REFUND);
        for (DeliveryEnd end : List.of(DeliveryEnd.CANCELLED_AFTER_PICKUP, DeliveryEnd.FAILED)) {
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, "COURIER")).isEqualTo(Action.REFUND);
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, "PLATFORM")).isEqualTo(Action.REFUND);
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, "SENDER")).isEqualTo(Action.RELEASE);
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, "RECIPIENT")).isEqualTo(Action.RELEASE);
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, "NONE")).isEqualTo(Action.RELEASE);
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, "RELAY")).isEqualTo(Action.NONE);
            assertThat(EscrowOutcomePolicy.onDeliveryEnd(end, null)).isEqualTo(Action.NONE);
        }
    }

    @Test
    @DisplayName("dispute closed: restored → unfreeze; compensated → refund; return/redelivery → refund unless the outbound leg was done without the courier's fault (QO-23)")
    void disputeClosed() {
        assertThat(EscrowOutcomePolicy.onDisputeClosed(null, false, false)).isEqualTo(Action.UNFREEZE);
        assertThat(EscrowOutcomePolicy.onDisputeClosed("RESTORE_PREVIOUS", false, false)).isEqualTo(Action.UNFREEZE);
        assertThat(EscrowOutcomePolicy.onDisputeClosed("TOTAL_LOSS_COMPENSATED", false, true)).isEqualTo(Action.REFUND);
        assertThat(EscrowOutcomePolicy.onDisputeClosed("DAMAGE_COMPENSATED", false, true)).isEqualTo(Action.REFUND);
        assertThat(EscrowOutcomePolicy.onDisputeClosed("RETURN_TO_SENDER_ORDERED", false, true)).isEqualTo(Action.RELEASE);
        assertThat(EscrowOutcomePolicy.onDisputeClosed("RETURN_TO_SENDER_ORDERED", true, true)).isEqualTo(Action.REFUND);
        assertThat(EscrowOutcomePolicy.onDisputeClosed("REDELIVERY_ORDERED", false, false)).isEqualTo(Action.REFUND);
    }
}
