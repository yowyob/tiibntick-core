package com.yowyob.tiibntick.core.delivery.domain.policy;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@code AT_RELAY_POINT -> DELIVERED} transition added so a client retrieving a
 * parcel directly from a relay point can be reflected as a genuine delivery completion in
 * {@code tnt-delivery-core} — closing a loop that previously left AT_RELAY_POINT as a
 * permanent dead end for relay-point deliveries (GOFP review finding #3).
 *
 * @author MANFOUO BRAUN
 */
class DeliveryStateTransitionPolicyTest {

    @Test
    void atRelayPoint_canTransitionDirectlyToDelivered() {
        assertThat(DeliveryStateTransitionPolicy.isAllowed(
                DeliveryStatus.AT_RELAY_POINT, DeliveryStatus.DELIVERED)).isTrue();
    }

    @Test
    void atRelayPoint_stillAllowsResumeAndFailureAndPause() {
        assertThat(DeliveryStateTransitionPolicy.allowedTransitionsFrom(DeliveryStatus.AT_RELAY_POINT))
                .containsExactlyInAnyOrder(
                        DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED,
                        DeliveryStatus.FAILED, DeliveryStatus.PAUSED_BY_INCIDENT);
    }

    @Test
    void delivered_remainsTerminal() {
        assertThat(DeliveryStateTransitionPolicy.allowedTransitionsFrom(DeliveryStatus.DELIVERED)).isEmpty();
    }

    @Test
    void inTransit_stillAllowsAtRelayPointAndDelivered() {
        assertThat(DeliveryStateTransitionPolicy.isAllowed(
                DeliveryStatus.IN_TRANSIT, DeliveryStatus.AT_RELAY_POINT)).isTrue();
        assertThat(DeliveryStateTransitionPolicy.isAllowed(
                DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED)).isTrue();
    }
}
