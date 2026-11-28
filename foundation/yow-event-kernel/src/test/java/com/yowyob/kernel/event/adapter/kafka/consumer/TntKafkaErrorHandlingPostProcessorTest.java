package com.yowyob.kernel.event.adapter.kafka.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("TntKafkaErrorHandlingPostProcessor: common retries + tnt.dlq on every listener factory (lot L0)")
class TntKafkaErrorHandlingPostProcessorTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<io.micrometer.core.instrument.MeterRegistry> noRegistry = mock(ObjectProvider.class);

    @Test
    void installsTheCommonHandlerOnAFactoryWithoutOne() {
        var processor = new TntKafkaErrorHandlingPostProcessor(new MockEnvironment(), noRegistry);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();

        processor.postProcessAfterInitialization(factory, "someModuleFactory");

        assertThat(TntKafkaErrorHandlingPostProcessor.errorHandlerOf(factory)).isInstanceOf(DefaultErrorHandler.class);
        assertThat(processor.maxAttempts()).isEqualTo(4);
    }

    @Test
    void keepsTheHandlerAFactoryAlreadyHas() {
        var processor = new TntKafkaErrorHandlingPostProcessor(new MockEnvironment(), noRegistry);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        CommonErrorHandler own = new CommonLoggingErrorHandler();
        factory.setCommonErrorHandler(own);

        processor.postProcessAfterInitialization(factory, "syncKafkaListenerContainerFactory");

        assertThat(TntKafkaErrorHandlingPostProcessor.errorHandlerOf(factory)).isSameAs(own);
    }

    @Test
    void ignoresOtherBeansAndReadsMaxAttemptsFromTheProperty() {
        var env = new MockEnvironment().withProperty(TntKafkaErrorHandlingPostProcessor.MAX_ATTEMPTS_PROPERTY, "2");
        var processor = new TntKafkaErrorHandlingPostProcessor(env, noRegistry);
        Object other = new Object();
        assertThat(processor.postProcessAfterInitialization(other, "x")).isSameAs(other);
        assertThat(processor.maxAttempts()).isEqualTo(2);
        assertThat(processor.newErrorHandler()).isNotNull();
    }

    @Test
    void refusesLessThanOneAttempt() {
        var env = new MockEnvironment().withProperty(TntKafkaErrorHandlingPostProcessor.MAX_ATTEMPTS_PROPERTY, "0");
        assertThatThrownBy(() -> new TntKafkaErrorHandlingPostProcessor(env, noRegistry))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deadLetterSerializerWritesRecordsBackUnchanged() {
        var serializer = new DeadLetterValueSerializer();
        byte[] raw = {1, 2, 3};
        assertThat(serializer.serialize("t", raw)).isSameAs(raw);
        assertThat(serializer.serialize("t", "{\"a\":1}")).isEqualTo("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        assertThat(serializer.serialize("t", null)).isNull();
        assertThat(new String(serializer.serialize("t", Map.of("at", Instant.parse("2026-11-26T10:00:00Z"))),
            StandardCharsets.UTF_8)).isEqualTo("{\"at\":\"2026-11-26T10:00:00Z\"}");
    }

    @Test
    void dlqTopicIsTheCommonOne() {
        assertThat(TntKafkaErrorHandlingPostProcessor.DLQ_TOPIC).isEqualTo("tnt.dlq");
    }
}
