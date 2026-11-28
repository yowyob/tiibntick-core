package com.yowyob.tiibntick.bootstrap.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.PutOrganizationCommand;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.UpdatePlatformCommand;
import com.yowyob.tiibntick.core.administration.application.port.in.ResolveDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.domain.event.DeliveryThresholdsChanged;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ThresholdSource;
import com.yowyob.tiibntick.core.organization.application.port.out.AgencyRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1 « evt-seuils-plateforme » on the assembled application, real PostGIS, real Kafka, real Redis: the delivery
 * threshold routes are mapped, the real organization-core agency lookup decides who may override, a change is stored
 * and its {@code DeliveryThresholdsChanged} event goes through the outbox to {@code tnt.administration.events}, where a
 * real consumer reads it (key = tenant, headers, body {@code {eventType, module, tenantId, aggregateId, payload}}).
 *
 * <p>Needs live infrastructure, like {@link KafkaAsyncWiringTest} (same variables): skipped unless
 * {@code TNT_IT_KAFKA_BOOTSTRAP} is set.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@DisplayName("Delivery thresholds — real context, real broker, real database")
class DeliveryThresholdsWiringTest {

    private static final String SYSTEM_TENANT = "d3b07384-d9a0-4c9c-b7d1-5f2a1b6c0e91";

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BOOTSTRAP", () -> System.getenv("TNT_IT_KAFKA_BOOTSTRAP"));
        registry.add("DB_HOST", () -> env("TNT_IT_DB_HOST", "localhost"));
        registry.add("DB_PORT", () -> env("TNT_IT_DB_PORT", "5433"));
        registry.add("DB_NAME", () -> env("TNT_IT_DB_NAME", "tiibntick_core"));
        registry.add("DB_USER", () -> env("TNT_IT_DB_USER", "tiibntick"));
        registry.add("DB_PASSWORD", () -> env("TNT_IT_DB_PASSWORD", "tiibntick_pass"));
        registry.add("REDIS_HOST", () -> env("TNT_IT_REDIS_HOST", "localhost"));
        registry.add("REDIS_PORT", () -> env("TNT_IT_REDIS_PORT", "6379"));
        registry.add("TNT_SYSTEM_TENANT_ID", () -> SYSTEM_TENANT);
        registry.add("ELASTICSEARCH_ENABLED", () -> "false");
        registry.add("TNT_SENTIMENT_ENABLED", () -> "false");
        registry.add("JWT_AUTO_GENERATE_KEY_PAIR", () -> "true");
        registry.add("TNT_AUTH_ALLOW_ANONYMOUS", () -> "true");
        registry.add("TNT_ROLES_PROVISION_ON_STARTUP", () -> "false");
        registry.add("yow.event.outbox.relay.poll-interval-ms", () -> "1000");
        registry.add("yow.event.outbox.relay.initial-delay", () -> "1s");
        registry.add("yow.event.outbox.relay.pending-grace", () -> "5s");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Autowired ManageDeliveryThresholdsUseCase manage;
    @Autowired ResolveDeliveryThresholdsUseCase resolve;
    @Autowired AgencyRepositoryPort agencies;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @Autowired @Qualifier("tntObjectMapper") ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @Test
    @DisplayName("routes mapped; platform change and agency override stored, resolved and published on the real broker")
    void thresholdsEndToEnd() throws Exception {
        List<String> patterns = new ArrayList<>();
        handlerMapping.getHandlerMethods().keySet()
                .forEach(info -> info.getPatternsCondition().getPatterns().forEach(p -> patterns.add(p.getPatternString())));
        assertThat(patterns).contains("/api/v1/admin/settings/delivery-thresholds",
                "/api/v1/admin/settings/delivery-thresholds/organizations",
                "/api/v1/organizations/{organizationId}/settings/delivery-thresholds",
                "/api/v1/organizations/{organizationId}/settings/delivery-thresholds/effective");

        UUID tenant = UUID.fromString(SYSTEM_TENANT);
        UUID admin = UUID.randomUUID();
        UUID agencyOrg = UUID.randomUUID();
        agencies.save(Agency.create(agencyOrg, tenant, "Agence L1 " + agencyOrg, "RC-L1", "XAF")).block();

        // platform change (attempts 4, relay bounds 2..14)
        PlatformDeliveryThresholds current = manage.getPlatform().block();
        Map<DeliveryThreshold, Integer> values = new EnumMap<>(current.values().asMap());
        int attempts = current.get(DeliveryThreshold.MAX_DELIVERY_ATTEMPTS) == 4 ? 5 : 4;
        values.put(DeliveryThreshold.MAX_DELIVERY_ATTEMPTS, attempts);
        PlatformDeliveryThresholds saved = manage.updatePlatform(new UpdatePlatformCommand(
                DeliveryThresholdValues.of(values), new RelayStorageBounds(2, 14), current.version(), admin, tenant))
                .block();
        assertThat(saved.version()).isEqualTo(current.version() + 1);

        // agency override, resolved through the real organization-core lookup
        manage.putOrganizationOverride(new PutOrganizationCommand(agencyOrg,
                DeliveryThresholdValues.of(Map.of(DeliveryThreshold.RELAY_STORAGE_DAYS, 10)), null, admin, tenant))
                .block();
        var effective = resolve.resolve(agencyOrg).block();
        assertThat(effective.agencyOrganization()).isTrue();
        assertThat(effective.get(DeliveryThreshold.RELAY_STORAGE_DAYS).source()).isEqualTo(ThresholdSource.ORGANIZATION);
        assertThat(effective.get(DeliveryThreshold.MAX_DELIVERY_ATTEMPTS).value()).isEqualTo(attempts);
        assertThat(resolve.resolve(UUID.randomUUID()).block().agencyOrganization()).isFalse();

        // both events on the real broker
        ConsumerRecord<String, String> platformEvent = awaitRecord(r -> isThresholdEvent(r)
                && r.value().contains(DeliveryThresholdsChanged.PLATFORM_AGGREGATE_ID.toString())
                && r.value().contains("\"version\":" + saved.version()));
        assertThat(platformEvent.key()).isEqualTo(SYSTEM_TENANT);
        assertThat(header(platformEvent, "X-Yow-Aggregate-Type")).isEqualTo("Administration");
        JsonNode body = mapper.readTree(platformEvent.value());
        assertThat(body.path("eventType").asText()).isEqualTo("DeliveryThresholdsChanged");
        assertThat(body.path("module").asText()).isEqualTo("administration");
        assertThat(body.path("tenantId").asText()).isEqualTo(SYSTEM_TENANT);
        JsonNode payload = body.path("payload");
        assertThat(payload.path("scope").asText()).isEqualTo("PLATFORM");
        assertThat(payload.path("thresholds").path("maxDeliveryAttempts").asInt()).isEqualTo(attempts);
        assertThat(payload.path("independentRelayStorageMaxDays").asInt()).isEqualTo(14);
        assertThat(payload.path("updatedBy").asText()).isEqualTo(admin.toString());

        ConsumerRecord<String, String> orgEvent = awaitRecord(r -> isThresholdEvent(r)
                && r.value().contains(agencyOrg.toString()));
        JsonNode orgPayload = mapper.readTree(orgEvent.value()).path("payload");
        assertThat(orgPayload.path("scope").asText()).isEqualTo("ORGANIZATION");
        assertThat(orgPayload.path("thresholds").path("relayStorageDays").asInt()).isEqualTo(10);
        assertThat(orgPayload.path("thresholds").path("maxDeliveryAttempts").isNull()).isTrue();
        assertThat(orgPayload.path("previousThresholds").isNull()).isTrue();

        manage.deleteOrganizationOverride(agencyOrg, admin, tenant).block();
    }

    private static boolean isThresholdEvent(ConsumerRecord<String, String> r) {
        return "DeliveryThresholdsChanged".equals(header(r, "X-Yow-Event-Type"));
    }

    private static String header(ConsumerRecord<String, String> r, String name) {
        Header h = r.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private ConsumerRecord<String, String> awaitRecord(Predicate<ConsumerRecord<String, String>> match) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-it-l1-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TntTopics.ADMINISTRATION_EVENTS));
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no matching record on " + TntTopics.ADMINISTRATION_EVENTS + " within 60s");
    }
}
