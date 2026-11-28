package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.kernel.event.adapter.kafka.consumer.TntKafkaErrorHandlingPostProcessor;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lot L0: aggregate topics, DLQ and socle beans declared by the bootstrap")
class TntKafkaAggregateTopicsTest {

    @Test
    void theFiveAggregateTopicsAreDeclaredWithDeletePolicyAndRetention() {
        TntKafkaTopicsConfig config = new TntKafkaTopicsConfig();
        ReflectionTestUtils.setField(config, "partitions", 3);
        ReflectionTestUtils.setField(config, "replicationFactor", 1);
        ReflectionTestUtils.setField(config, "aggregateRetentionMs", 2_592_000_000L);

        List<NewTopic> topics = List.of(config.tntParcelEvents(), config.tntDeliveryEvents(), config.tntIncidentEvents(),
            config.tntDisputeEvents(), config.tntAnnouncementEvents());

        assertThat(topics).extracting(NewTopic::name).containsExactly(TntTopics.PARCEL_EVENTS, TntTopics.DELIVERY_EVENTS,
            TntTopics.INCIDENT_EVENTS, TntTopics.DISPUTE_EVENTS, TntTopics.ANNOUNCEMENT_EVENTS);
        assertThat(topics).extracting(NewTopic::name)
            .containsExactlyInAnyOrder(java.util.Arrays.stream(TntAggregateType.values()).map(TntAggregateType::topic)
                .toArray(String[]::new));
        topics.forEach(topic -> {
            assertThat(topic.numPartitions()).isEqualTo(3);
            assertThat(topic.configs()).containsEntry(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
                .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "2592000000");
        });
        assertThat(config.tntDlq().name()).isEqualTo(TntTopics.DLQ);
    }

    @Test
    void theKernelDeadLetterTopicIsTheCommonDlqConstant() {
        // yow-event-kernel (L0) cannot see TntTopics (L1): both literals must stay equal
        assertThat(TntKafkaErrorHandlingPostProcessor.DLQ_TOPIC).isEqualTo(TntTopics.DLQ);
    }

    @Test
    void socleBeansFollowTheirProperties() {
        TntKafkaConfig config = new TntKafkaConfig();
        TntEventMasking masking = config.tntEventMasking(2);
        assertThat(masking.approx(3.8666781)).isEqualTo(3.87);
        ReactiveListenerSupport support = config.reactiveListenerSupport(Duration.ofSeconds(30));
        assertThat(support.timeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
