package com.yowyob.kernel.event.adapter.kafka.consumer;

import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import com.yowyob.kernel.event.config.YowEventKernelProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IdempotentRecordInterceptor — one effect per event and consumer group")
class IdempotentRecordInterceptorTest {

    /** In-memory store standing in for event_bus.processed_events. */
    static final class MemoryStore implements ProcessedEventStorePort {
        final Set<String> keys = new HashSet<>();
        boolean down;

        @Override
        public Mono<Boolean> isProcessed(String group, String topic, String eventId) {
            return down ? Mono.error(new IllegalStateException("db down"))
                    : Mono.just(keys.contains(group + "|" + topic + "|" + eventId));
        }

        @Override
        public Mono<Void> markProcessed(String group, String topic, String eventId) {
            return down ? Mono.error(new IllegalStateException("db down"))
                    : Mono.fromRunnable(() -> keys.add(group + "|" + topic + "|" + eventId));
        }

        @Override
        public Mono<Long> purgeOlderThan(java.time.Duration retention) {
            return Mono.just(0L);
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final EventMetricsPort metrics = mock(EventMetricsPort.class);
    private final YowEventKernelProperties properties = new YowEventKernelProperties();
    private IdempotentRecordInterceptor interceptor;

    @SuppressWarnings("unchecked")
    private Consumer<Object, Object> consumer(String group) {
        Consumer<Object, Object> consumer = mock(Consumer.class);
        when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata(group));
        return consumer;
    }

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("store", store);
        beans.addBean("metrics", metrics);
        beans.addBean("properties", properties);
        interceptor = new IdempotentRecordInterceptor(beans.getBeanProvider(ProcessedEventStorePort.class),
                beans.getBeanProvider(EventMetricsPort.class), beans.getBeanProvider(YowEventKernelProperties.class));
    }

    /** Simulates the container: intercept, listener, success/failure. Returns true if the listener ran. */
    private boolean deliver(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer, boolean listenerFails) {
        ConsumerRecord<Object, Object> passed = interceptor.intercept(record, consumer);
        if (passed == null) {
            interceptor.success(record, consumer);
            return false;
        }
        if (listenerFails) {
            interceptor.failure(record, new RuntimeException("boom"), consumer);
        } else {
            interceptor.success(record, consumer);
        }
        return true;
    }

    @Test
    @DisplayName("the same event delivered twice to the same group runs the listener once")
    void duplicateIsSkipped() {
        var c = consumer("tnt-notify-core");
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-1"), c, false)).isTrue();
        assertThat(deliver(KafkaEventIdsTest.record("{}", 2, "X-Yow-Envelope-Id", "env-1"), c, false)).isFalse();
        verify(metrics).recordDuplicateSkipped("tnt-notify-core", "tnt.topic");
    }

    @Test
    @DisplayName("another consumer group still processes the event once")
    void otherGroupProcesses() {
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-2"), consumer("a"), false)).isTrue();
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-2"), consumer("b"), false)).isTrue();
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-2"), consumer("b"), false)).isFalse();
    }

    @Test
    @DisplayName("a listener that failed leaves the event unmarked: the redelivery is processed")
    void failureIsNotMarked() {
        var c = consumer("g");
        var r = KafkaEventIdsTest.record("{\"eventId\":\"e-3\"}", 5);
        assertThat(deliver(r, c, true)).isTrue();
        assertThat(store.keys).isEmpty();
        assertThat(deliver(r, c, false)).isTrue();
        assertThat(deliver(r, c, false)).isFalse();
    }

    @Test
    @DisplayName("an explicit replay (X-Yow-Replay: true) is never skipped")
    void replayIsProcessed() {
        var c = consumer("g");
        deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-4"), c, false);
        assertThat(deliver(KafkaEventIdsTest.record("{}", 9, "X-Yow-Envelope-Id", "env-4", "X-Yow-Replay", "true"), c, false))
                .isTrue();
    }

    @Test
    @DisplayName("store unavailable: fail-open, the record is processed")
    void failOpen() {
        store.down = true;
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-5"), consumer("g"), false)).isTrue();
    }

    @Test
    @DisplayName("disabled or excluded topic: pass-through, nothing recorded")
    void disabledAndExcluded() {
        properties.getConsumerIdempotency().setExcludedTopics(List.of("tnt.topic"));
        var c = consumer("g");
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-6"), c, false)).isTrue();
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-6"), c, false)).isTrue();
        assertThat(store.keys).isEmpty();
        properties.getConsumerIdempotency().setExcludedTopics(List.of());
        properties.getConsumerIdempotency().setEnabled(false);
        assertThat(deliver(KafkaEventIdsTest.record("{}", 1, "X-Yow-Envelope-Id", "env-6"), c, false)).isTrue();
        assertThat(store.keys).isEmpty();
    }

    @Test
    @DisplayName("the post-processor installs the filter on every listener container factory, keeping an existing interceptor")
    @SuppressWarnings("unchecked")
    void postProcessorDecoratesFactories() {
        var bpp = new IdempotentListenerContainerFactoryPostProcessor(interceptor);
        var plain = new ConcurrentKafkaListenerContainerFactory<String, String>();
        bpp.postProcessAfterInitialization(plain, "plainFactory");
        assertThat(plain.getRecordInterceptor()).isSameAs((Object) interceptor);

        RecordInterceptor<String, String> own = mock(RecordInterceptor.class);
        var withOwn = new ConcurrentKafkaListenerContainerFactory<String, String>();
        withOwn.setRecordInterceptor(own);
        bpp.postProcessAfterInitialization(withOwn, "ownFactory");
        assertThat(withOwn.getRecordInterceptor()).isInstanceOf(CompositeRecordInterceptor.class);

        bpp.postProcessAfterInitialization(plain, "plainFactory");
        assertThat(plain.getRecordInterceptor()).isSameAs((Object) interceptor);
        Object notAFactory = new Object();
        assertThat(bpp.postProcessAfterInitialization(notAFactory, "x")).isSameAs(notAFactory);
        verify(metrics, never()).recordDuplicateSkipped(anyString(), anyString());
    }
}
