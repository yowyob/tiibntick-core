package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lot L7: the notification work is awaited; a failed send or an unreadable record reaches the container. */
class FreelancerOrgKafkaEventConsumerBlockingTest {

    private final ISendNotificationUseCase send = mock(ISendNotificationUseCase.class);
    private final FreelancerOrgKafkaEventConsumer consumer = new FreelancerOrgKafkaEventConsumer(send,
            new ObjectMapper(), new NotifyProperties(), ReactiveListenerSupport.withDefaultTimeout());

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("tnt.admin.freelancer_org.suspended", 0, 0L, "k", json);
    }

    @Test
    void everyChannelIsSentBeforeTheListenerReturns() {
        AtomicInteger sent = new AtomicInteger();
        when(send.send(any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> Mono.fromRunnable(sent::incrementAndGet).then(Mono.empty()));

        consumer.handleOrgSuspended(record("{\"tenantId\":\"t\",\"orgId\":\"o\",\"reason\":\"r\"}"));

        assertThat(sent).hasValue(3);
    }

    @Test
    void aFailedSendOrAnUnreadableRecordReachesTheContainer() {
        when(send.send(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("gateway down")));
        assertThatThrownBy(() -> consumer.handleOrgUnsuspended(record("{\"tenantId\":\"t\",\"orgId\":\"o\"}")))
                .hasMessageContaining("gateway down");
        assertThatThrownBy(() -> consumer.handleKycRejected(record("not json"))).isNotNull();
    }
}
