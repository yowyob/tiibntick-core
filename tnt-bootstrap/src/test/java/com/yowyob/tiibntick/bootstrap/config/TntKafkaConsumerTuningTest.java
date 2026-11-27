package com.yowyob.tiibntick.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * The settings under test are the ones no yaml can reach, because all 22 consumer
 * modules build their own configuration map. These tests pin both halves of the
 * contract: the defaults do get applied, and a value a module chose for itself is
 * never overwritten.
 */
@DisplayName("Kafka consumer tuning reaches every factory without overruling one")
class TntKafkaConsumerTuningTest {

    private static final int FETCH_WAIT_MS = 5_000;
    private static final int HEARTBEAT_MS = 10_000;

    private static TntKafkaConsumerTuning tuning() {
        return new TntKafkaConsumerTuning(FETCH_WAIT_MS, HEARTBEAT_MS, 0);
    }

    private static DefaultKafkaConsumerFactory<String, String> factoryWith(Map<String, Object> extra) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "a-group");
        props.putAll(extra);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Test
    @DisplayName("a factory that declares neither setting receives both")
    void appliesBothDefaultsWhenTheModuleDeclaredNeither() {
        DefaultKafkaConsumerFactory<String, String> factory = factoryWith(Map.of());

        tuning().postProcessAfterInitialization(factory, "someConsumerFactory");

        assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_WAIT_MS)
                .containsEntry(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_MS);
    }

    /**
     * The control that makes the assertion above mean something: an untouched factory
     * must NOT carry these keys. Without this, the test would pass just as well if
     * Kafka's defaults were already materialised into the map.
     */
    @Test
    @DisplayName("control — an untouched factory carries neither setting")
    void anUntouchedFactoryCarriesNeitherSetting() {
        DefaultKafkaConsumerFactory<String, String> factory = factoryWith(Map.of());

        assertThat(factory.getConfigurationProperties())
                .doesNotContainKey(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG)
                .doesNotContainKey(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG);
    }

    @Test
    @DisplayName("a value the module chose for itself survives untouched")
    void doesNotOverwriteWhatTheModuleDeclared() {
        DefaultKafkaConsumerFactory<String, String> factory = factoryWith(Map.of(
                ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100,
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 1_000));

        tuning().postProcessAfterInitialization(factory, "opinionatedConsumerFactory");

        assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100)
                .containsEntry(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 1_000);
    }

    @Test
    @DisplayName("a session timeout too short for the heartbeat leaves that factory alone")
    void leavesHeartbeatAloneWhenTheSessionTimeoutIsTooShort() {
        DefaultKafkaConsumerFactory<String, String> factory = factoryWith(Map.of(
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 12_000));

        tuning().postProcessAfterInitialization(factory, "shortSessionConsumerFactory");

        assertThat(factory.getConfigurationProperties())
                .doesNotContainKey(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG)
                // the fetch wait is independent of the session timeout, so it still applies
                .containsEntry(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_WAIT_MS);
    }

    @Test
    @DisplayName("concurrency is left to each module unless asked for")
    void leavesConcurrencyToTheModuleByDefault() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = spy(
                new ConcurrentKafkaListenerContainerFactory<String, String>());

        new TntKafkaConsumerTuning(FETCH_WAIT_MS, HEARTBEAT_MS, 0)
                .postProcessAfterInitialization(factory, "someContainerFactory");

        verify(factory, never()).setConcurrency(any());
    }

    @Test
    @DisplayName("concurrency is applied when the property asks for it")
    void appliesConcurrencyWhenConfigured() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = spy(
                new ConcurrentKafkaListenerContainerFactory<String, String>());

        new TntKafkaConsumerTuning(FETCH_WAIT_MS, HEARTBEAT_MS, 1)
                .postProcessAfterInitialization(factory, "someContainerFactory");

        verify(factory).setConcurrency(1);
    }

    /**
     * Spring's poll returning before the fetch would discard the saving, so the two
     * have to stay matched. 5 s against the 5 s default needs no change; a longer
     * fetch wait does.
     */
    @Test
    @DisplayName("a fetch wait longer than the poll timeout raises the poll timeout")
    void raisesPollTimeoutToMatchALongerFetchWait() {
        ConcurrentKafkaListenerContainerFactory<String, String> untouched =
                new ConcurrentKafkaListenerContainerFactory<>();
        long defaultPollTimeout = untouched.getContainerProperties().getPollTimeout();
        assertThat(defaultPollTimeout).isEqualTo(5_000L);

        ConcurrentKafkaListenerContainerFactory<String, String> matched =
                new ConcurrentKafkaListenerContainerFactory<>();
        new TntKafkaConsumerTuning(FETCH_WAIT_MS, HEARTBEAT_MS, 0)
                .postProcessAfterInitialization(matched, "matchedContainerFactory");
        assertThat(matched.getContainerProperties().getPollTimeout())
                .as("5 s fetch wait against the 5 s default needs no change")
                .isEqualTo(5_000L);

        ConcurrentKafkaListenerContainerFactory<String, String> stretched =
                new ConcurrentKafkaListenerContainerFactory<>();
        new TntKafkaConsumerTuning(8_000, HEARTBEAT_MS, 0)
                .postProcessAfterInitialization(stretched, "stretchedContainerFactory");
        assertThat(stretched.getContainerProperties().getPollTimeout()).isEqualTo(8_000L);
    }

    @Test
    @DisplayName("a heartbeat Kafka would reject is refused at startup, by name")
    void refusesAHeartbeatKafkaWouldReject() {
        assertThatThrownBy(() -> new TntKafkaConsumerTuning(FETCH_WAIT_MS, 20_000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tnt.kafka.consumer.heartbeat-interval-ms");

        assertThatThrownBy(() -> new TntKafkaConsumerTuning(0, HEARTBEAT_MS, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tnt.kafka.consumer.fetch-max-wait-ms");
    }

    @Test
    @DisplayName("beans that are neither kind of factory pass through untouched")
    void passesUnrelatedBeansThrough() {
        Object unrelated = new Object();

        assertThat(tuning().postProcessAfterInitialization(unrelated, "unrelated"))
                .isSameAs(unrelated);
    }

    /**
     * The test that decides whether any of this runs at all. A BeanPostProcessor that
     * resolves {@code @Value} in its constructor can fail to be registered, or be
     * registered too late to see other beans — in which case every assertion above
     * would still pass while production stayed untuned. This mounts a real Spring
     * context and reads the factory back out of it.
     */
    @Test
    @DisplayName("a real Spring context applies the tuning to the factory beans it holds")
    void aRealSpringContextAppliesTheTuning() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TuningScenario.class, TntKafkaConsumerTuning.class);
            context.refresh();

            DefaultKafkaConsumerFactory<?, ?> consumerFactory =
                    context.getBean(DefaultKafkaConsumerFactory.class);
            assertThat(consumerFactory.getConfigurationProperties())
                    .as("the module's own factory bean, as the context handed it back")
                    .containsEntry(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_WAIT_MS)
                    .containsEntry(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_MS);
        }
    }

    /**
     * Stands in for one of the 22 modules: a factory bean built from its own hardcoded
     * map, exactly as they all do it.
     */
    @Configuration
    static class TuningScenario {

        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        DefaultKafkaConsumerFactory<String, String> aModuleConsumerFactory() {
            return factoryWith(Map.of());
        }
    }
}
