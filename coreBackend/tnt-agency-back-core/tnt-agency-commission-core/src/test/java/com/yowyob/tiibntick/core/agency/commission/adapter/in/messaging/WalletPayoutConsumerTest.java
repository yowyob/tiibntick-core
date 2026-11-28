package com.yowyob.tiibntick.core.agency.commission.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7: the listener awaits its work, acknowledges only on success and lets failures reach the container. */
class WalletPayoutConsumerTest {

    private final CommissionService service = mock(CommissionService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final WalletPayoutConsumer consumer = new WalletPayoutConsumer(service, new ObjectMapper(),
            ReactiveListenerSupport.withDefaultTimeout());

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("tnt.billing.wallet.payment_confirmed", 0, 0L, "k", json);
    }

    @Test
    void confirmsTheCommissionAndAcknowledgesOnlyAfterTheWorkCompleted() throws Exception {
        UUID commission = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        AtomicBoolean done = new AtomicBoolean();
        when(service.confirmWalletPayment(tenant, commission))
                .thenReturn(Mono.fromRunnable(() -> done.set(true)).then());

        consumer.onWalletEvent(record("{\"invoiceId\":\"" + commission + "\",\"tenantId\":\"" + tenant + "\"}"), ack);

        assertThat(done).isTrue();
        verify(ack).acknowledge();
    }

    @Test
    void aProcessingFailureReachesTheContainerAndIsNotAcknowledged() {
        UUID commission = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(service.confirmWalletPayment(tenant, commission)).thenReturn(Mono.error(new IllegalStateException("db")));

        assertThatThrownBy(() -> consumer.onWalletEvent(
                record("{\"invoiceId\":\"" + commission + "\",\"tenantId\":\"" + tenant + "\"}"), ack))
                .hasMessageContaining("db");
        verify(ack, never()).acknowledge();
    }

    @Test
    void anUnreadableRecordReachesTheContainer() {
        assertThatThrownBy(() -> consumer.onWalletEvent(record("not json"), ack)).isNotNull();
        verify(ack, never()).acknowledge();
    }

    @Test
    void aPaymentThatIsNotACommissionIsAcknowledgedWithoutEffect() throws Exception {
        consumer.onWalletEvent(record("{\"paymentId\":\"p\",\"tenantId\":\"" + UUID.randomUUID() + "\"}"), ack);
        consumer.onWalletEvent(record("{\"invoiceId\":\"INV-42\",\"tenantId\":\"" + UUID.randomUUID() + "\"}"), ack);

        verify(service, never()).confirmWalletPayment(any(), any());
        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
    }
}
