package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.wallet.application.service.PaymentLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@DisplayName("Lot L5: tnt.delivery.events and tnt.dispute.events → escrow / cash collection")
class WalletPaymentLifecycleConsumerTest {

    private final PaymentLifecycleService lifecycle = mock(PaymentLifecycleService.class);
    private WalletPaymentLifecycleConsumer consumer;
    private final UUID delivery = UUID.randomUUID();
    private final UUID dispute = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new WalletPaymentLifecycleConsumer(lifecycle, new ObjectMapper(),
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.class));
        when(lifecycle.delivered(any(), any())).thenReturn(Mono.empty());
        when(lifecycle.depositedAtRelay(any(), anyBoolean())).thenReturn(Mono.empty());
        when(lifecycle.cancelled(any(), any(), any())).thenReturn(Mono.empty());
        when(lifecycle.failed(any(), any(), any())).thenReturn(Mono.empty());
        when(lifecycle.disputeOpened(any(), any())).thenReturn(Mono.empty());
        when(lifecycle.disputeClosed(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(lifecycle.disputeCompensated(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private String deliveryEvent(String type, String previousStatus, String details, String payload) {
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type
                + "\",\"aggregateType\":\"DELIVERY\",\"aggregateId\":\"" + delivery + "\",\"previousState\":"
                + (previousStatus == null ? "null" : "{\"status\":\"" + previousStatus + "\"}")
                + ",\"payload\":" + payload + ",\"details\":" + details + "}";
    }

    private String disputeEvent(String type, String resolution, String responsible) {
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type
                + "\",\"aggregateType\":\"DISPUTE\",\"aggregateId\":\"" + dispute + "\",\"tenantId\":\"" + tenant
                + "\",\"payload\":{\"deliveryId\":\"" + delivery + "\",\"compensationAmount\":\"15000.00\","
                + "\"currency\":\"XAF\",\"resolutionType\":" + (resolution == null ? "null" : "\"" + resolution + "\"")
                + ",\"responsibleParty\":" + (responsible == null ? "null" : "\"" + responsible + "\"")
                + "},\"details\":{}}";
    }

    @Test
    @DisplayName("each delivery end goes to its lifecycle step with the fields the policy needs")
    void deliveryEvents() throws Exception {
        consumer.onDeliveryEvent(deliveryEvent("DeliveryDelivered", "OUT_FOR_DELIVERY", "{}",
                "{\"agreedAmount\":\"3000.00\"}"));
        verify(lifecycle).delivered(delivery, new BigDecimal("3000.00"));

        consumer.onDeliveryEvent(deliveryEvent("DeliveryAtRelayHub", "IN_TRANSIT", "{\"atDestination\":true}", "{}"));
        verify(lifecycle).depositedAtRelay(delivery, true);

        consumer.onDeliveryEvent(deliveryEvent("DeliveryCancelled", "ASSIGNED",
                "{\"reason\":\"x\",\"cancelledBy\":\"SENDER\"}", "{}"));
        verify(lifecycle).cancelled(delivery, "ASSIGNED", "SENDER");

        consumer.onDeliveryEvent(deliveryEvent("DeliveryFailed", "OUT_FOR_DELIVERY",
                "{\"reason\":\"MAX_ATTEMPTS_REACHED\",\"responsibleParty\":\"RECIPIENT\"}", "{}"));
        verify(lifecycle).failed(delivery, "MAX_ATTEMPTS_REACHED", "RECIPIENT");
    }

    @Test
    @DisplayName("QO-11 / D-153: a delivery event naming a replacement courier records the courier legs")
    void courierChangeRecorded() throws Exception {
        when(lifecycle.courierReplaced(any())).thenReturn(Mono.empty());
        UUID first = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        String event = deliveryEvent("DeliveryInTransit", "INCIDENT_HANDOVER", "{\"replacedCourierId\":\"" + first
                + "\",\"replacementCourierId\":\"" + replacement + "\",\"replacedLegKm\":3.214,"
                + "\"remainingKm\":null}", "{}").replace("\"aggregateType\"", "\"tenantId\":\"" + tenant
                + "\",\"aggregateVersion\":7,\"aggregateType\"");
        consumer.onDeliveryEvent(event);
        org.mockito.ArgumentCaptor<com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg> leg =
                org.mockito.ArgumentCaptor.forClass(com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg.class);
        verify(lifecycle).courierReplaced(leg.capture());
        org.assertj.core.api.Assertions.assertThat(leg.getValue().deliveryId()).isEqualTo(delivery);
        org.assertj.core.api.Assertions.assertThat(leg.getValue().tenantId()).isEqualTo(tenant);
        org.assertj.core.api.Assertions.assertThat(leg.getValue().aggregateVersion()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(leg.getValue().replacedCourierId()).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThat(leg.getValue().replacementCourierId()).isEqualTo(replacement);
        org.assertj.core.api.Assertions.assertThat(leg.getValue().replacedLegKm()).isEqualByComparingTo("3.214");
        org.assertj.core.api.Assertions.assertThat(leg.getValue().remainingKm()).isNull();
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    @DisplayName("other event types and legacy records are ignored")
    void ignored() throws Exception {
        consumer.onDeliveryEvent(deliveryEvent("DeliveryPickedUp", "ASSIGNED", "{}", "{}"));
        consumer.onDeliveryEvent("{\"missionId\":\"" + delivery + "\",\"status\":\"DELIVERED\"}");
        consumer.onDisputeEvent(disputeEvent("DisputeUnderInvestigation", null, null));
        verifyNoInteractions(lifecycle);
    }

    @Test
    @DisplayName("dispute: opened freezes; a withdrawal/expiry restores; a ruling passes its resolution and responsible party")
    void disputeEvents() throws Exception {
        consumer.onDisputeEvent(disputeEvent("DisputeOpen", null, null));
        verify(lifecycle).disputeOpened(delivery, dispute);

        consumer.onDisputeEvent(disputeEvent("DisputeClosedWithdrawn", "RESTORE_PREVIOUS", null));
        verify(lifecycle).disputeClosed(delivery, dispute, null, null);

        consumer.onDisputeEvent(disputeEvent("DisputeClosedResolved", "RETURN_TO_SENDER_ORDERED", "COURIER"));
        verify(lifecycle).disputeClosed(delivery, dispute, "RETURN_TO_SENDER_ORDERED", "COURIER");

        consumer.onDisputeEvent(disputeEvent("DisputeCompensated", "TOTAL_LOSS_COMPENSATED", "COURIER"));
        verify(lifecycle).disputeClosed(delivery, dispute, "TOTAL_LOSS_COMPENSATED", "COURIER");
        // QO-26: the compensation becomes the responsible party's debt
        verify(lifecycle).disputeCompensated(tenant, delivery, dispute, "COURIER", new BigDecimal("15000.00"), "XAF");
        verify(lifecycle, times(1)).disputeCompensated(any(), any(), any(), any(), any(), any());
    }
}
