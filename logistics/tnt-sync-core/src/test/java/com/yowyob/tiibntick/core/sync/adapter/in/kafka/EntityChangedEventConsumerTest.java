package com.yowyob.tiibntick.core.sync.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L8: the offline sync indexes the aggregate topics (types PARCEL / DELIVERY / INCIDENT / DISPUTE / ANNOUNCEMENT). */
class EntityChangedEventConsumerTest {

    private static final String TENANT = UUID.randomUUID().toString();

    private final IEntityVersionRepository versions = mock(IEntityVersionRepository.class);
    private final EntityChangedEventConsumer consumer = new EntityChangedEventConsumer(versions, new ObjectMapper());
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private static String event(String id, String previous, String status) {
        String prev = previous == null ? "null" : "{\"status\":\"" + previous + "\"}";
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"X\",\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\","
                + "\"aggregateId\":\"" + id + "\",\"tenantId\":\"" + TENANT + "\","
                + "\"actor\":{\"type\":\"COURIER\",\"id\":\"c-1\"},\"previousState\":" + prev
                + ",\"payload\":{\"status\":\"" + status + "\"}}";
    }

    private EntityVersionRecord indexed(String topic, String value) {
        when(versions.upsert(any())).thenReturn(Mono.empty());
        consumer.onEntityChanged(new ConsumerRecord<>(topic, 0, 0L, "k", value), ack);
        ArgumentCaptor<EntityVersionRecord> captor = ArgumentCaptor.forClass(EntityVersionRecord.class);
        verify(versions).upsert(captor.capture());
        verify(ack).acknowledge();
        return captor.getValue();
    }

    @Test
    @DisplayName("a delivery transition is indexed as DELIVERY / STATUS_CHANGED by its actor, the envelope stored")
    void deliveryTransition() {
        String id = UUID.randomUUID().toString();
        String value = event(id, "ASSIGNED", "PICKED_UP");
        EntityVersionRecord record = indexed(TntTopics.DELIVERY_EVENTS, value);
        assertThat(record.aggregateType()).isEqualTo("DELIVERY");
        assertThat(record.aggregateId()).isEqualTo(id);
        assertThat(record.tenantId()).isEqualTo(TENANT);
        assertThat(record.operation()).isEqualTo(DeltaOperation.STATUS_CHANGED);
        assertThat(record.updatedByUserId()).isEqualTo("COURIER:c-1");
        assertThat(record.payloadJson()).isEqualTo(value);
    }

    @Test
    @DisplayName("a creation (no previousState) is CREATED")
    void creationAndUpdate() {
        assertThat(indexed(TntTopics.PARCEL_EVENTS, event("p", null, "CREATED")).operation())
                .isEqualTo(DeltaOperation.CREATED);
    }

    @Test
    void changeWithoutTransitionIsUpdated() {
        EntityVersionRecord record = indexed(TntTopics.INCIDENT_EVENTS, event("i", "TRIAGED", "TRIAGED"));
        assertThat(record.aggregateType()).isEqualTo("INCIDENT");
        assertThat(record.operation()).isEqualTo(DeltaOperation.UPDATED);
    }

    @Test
    @DisplayName("a legacy record (no schemaVersion) on an aggregate topic is acked and not indexed")
    void legacyRecordIgnored() {
        consumer.onEntityChanged(new ConsumerRecord<>(TntTopics.DISPUTE_EVENTS, 0, 0L, "k",
                "{\"eventType\":\"DisputeOpened\",\"tenantId\":\"" + TENANT + "\"}"), ack);
        verify(versions, never()).upsert(any());
        verify(ack).acknowledge();
    }
}
