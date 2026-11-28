package com.yowyob.tiibntick.core.agency.workforce.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7: the listener awaits its work, acknowledges only on success and lets failures reach the container. */
class ActorKycConsumerTest {

    private final AgencyDelivererService service = mock(AgencyDelivererService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final ActorKycConsumer consumer = new ActorKycConsumer(service, new ObjectMapper(),
            ReactiveListenerSupport.withDefaultTimeout());

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("tnt.actor.kyc.validated", 0, 0L, "k", json);
    }

    @Test
    void reactivatesTheDelivererThenAcknowledges() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(service.reactivateByActorId(tenant, actor)).thenReturn(Mono.empty());

        consumer.onKycValidated(record("{\"actorId\":\"" + actor + "\",\"tenantId\":\"" + tenant + "\"}"), ack);

        verify(service).reactivateByActorId(tenant, actor);
        verify(ack).acknowledge();
    }

    @Test
    void aFailureOrAMissingIdReachesTheContainer() {
        UUID actor = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(service.reactivateByActorId(tenant, actor)).thenReturn(Mono.error(new IllegalStateException("db")));

        assertThatThrownBy(() -> consumer.onKycValidated(
                record("{\"actorId\":\"" + actor + "\",\"tenantId\":\"" + tenant + "\"}"), ack))
                .hasMessageContaining("db");
        assertThatThrownBy(() -> consumer.onKycValidated(record("{\"tenantId\":\"" + tenant + "\"}"), ack))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ack, never()).acknowledge();
    }
}
