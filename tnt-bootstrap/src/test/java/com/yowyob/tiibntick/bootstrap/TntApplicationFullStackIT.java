package com.yowyob.tiibntick.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntEventSchemas;
import com.yowyob.tiibntick.common.kafka.TntPiiSentinels;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The complete application ({@code tnt-bootstrap}, every module) started on a REAL Kafka broker, a REAL
 * PostGIS database and a REAL Redis — required by decision [G.7] and created by lot L0 of the aggregate-events
 * workstream ({@code docs/architecture/chantiers/cible-evenements-colis-livraison.md} §6.0, test level 4). Each
 * later lot adds here one end-to-end scenario of its own scope.
 *
 * <p>L0 scenario — the common event socle, end to end: a {@link TntAggregateEvent} produced through the outbox
 * (direct publish and relay take-over) reaches a real {@code @KafkaListener} that <b>waits</b> for its reactive
 * work ({@link ReactiveListenerSupport}); the record is valid against {@code envelope.schema.json}, keyed by the
 * aggregate id and carries the {@code X-Yow-*} headers; the duplicate filter marks it processed in
 * {@code event_bus.processed_events} only after the work ended; a failing or timed-out work is retried
 * ({@code tnt.kafka.retry.max-attempts}) then published to {@code tnt.dlq}.
 *
 * <p>L8 scenario — one direct delivery driven by the real delivery services (assigned, picked up, in transit,
 * delivered): its parcel and delivery events reach the broker in order, valid and masked, the real
 * realtime and sync consumers apply them, and no consumer dead-letters any of them.
 *
 * <p>Infrastructure is started outside the build (Testcontainers is not used offline); the test is skipped unless
 * {@code TNT_IT_KAFKA_BOOTSTRAP} is set:
 * <pre>
 * docker run -d --name tnt-kafka-l0 -p 19492:9092 (KRaft, advertised localhost:19492) apache/kafka:3.8.0
 * docker run -d --name tnt-pg-l0 -p 15440:5432 -e POSTGRES_USER=tiibntick -e POSTGRES_PASSWORD=... -e POSTGRES_DB=tiibntick_core postgis/postgis:18-3.6
 * docker run -d --name tnt-redis-l0 -p 16386:6379 redis:7-alpine
 * TNT_IT_KAFKA_BOOTSTRAP=localhost:19492 TNT_IT_DB_PORT=15440 TNT_IT_DB_PASSWORD=... TNT_IT_REDIS_PORT=16386 \
 *   mvn -o verify -Pintegration-tests -pl tnt-bootstrap -am -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false \
 *       -Dit.test=TntApplicationFullStackIT -Dfailsafe.failIfNoSpecifiedTests=false
 * </pre>
 * Optional variables as in {@code KafkaAsyncWiringTest}: {@code TNT_IT_DB_HOST}, {@code TNT_IT_DB_NAME},
 * {@code TNT_IT_DB_USER}, {@code TNT_IT_REDIS_HOST}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@Import(TntApplicationFullStackIT.ProbeConfig.class)
@DisplayName("Full application on real Kafka / PostGIS / Redis — L0 event socle end to end")
class TntApplicationFullStackIT {

    private static final String SYSTEM_TENANT = "d3b07384-d9a0-4c9c-b7d1-5f2a1b6c0e91";
    private static final String PROBE_GROUP = "tnt-it-l0-socle-probe";
    private static final String TRIGGER_OK = "L0_PROBE";
    private static final String TRIGGER_FAILURE = "L0_PROBE_FAILURE";
    private static final String TRIGGER_TIMEOUT = "L0_PROBE_TIMEOUT";
    private static final Duration LISTENER_TIMEOUT = Duration.ofSeconds(2);
    private static final ObjectMapper MAPPER = TntEventJson.mapper();

    private static AdminClient admin;

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
        // the L8 scenario calls the delivery use cases directly, without a caller's permissions
        registry.add("TNT_ROLES_AOP_ENABLED", () -> "false");
        registry.add("yow.event.outbox.relay.poll-interval-ms", () -> "1000");
        registry.add("yow.event.outbox.relay.initial-delay", () -> "1s");
        registry.add("yow.event.outbox.relay.pending-grace", () -> "5s");
        registry.add(ReactiveListenerSupport.TIMEOUT_PROPERTY, () -> LISTENER_TIMEOUT.toString());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeAll
    static void openAdmin() {
        admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("TNT_IT_KAFKA_BOOTSTRAP")));
    }

    @AfterAll
    static void closeAdmin() {
        if (admin != null) {
            admin.close(Duration.ofSeconds(5));
        }
    }

    @Autowired ApplicationContext context;
    @Autowired PublishEventUseCase publishEvent;
    @Autowired EventEnvelopeRepository envelopes;
    @Autowired ProcessedEventStorePort processedEvents;
    @Autowired ReactiveRedisConnectionFactory redis;
    @Autowired TntEventMasking masking;
    @Autowired ProbeListener probe;
    @Autowired Map<String, AbstractKafkaListenerContainerFactory<?, ?, ?>> listenerFactories;
    // lot L8 scenario: real delivery services, real consumers (no mock in this class)
    @Autowired com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor deliveryTransitions;
    @Autowired com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase deliveryLifecycle;
    @Autowired com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository deliveries;
    @Autowired com.yowyob.tiibntick.core.realtime.domain.service.MissionTrackingCodeCache trackingCodes;
    @Autowired com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository entityVersions;

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 1. The application is up on the three real infrastructures
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the whole context starts on real PostGIS (Liquibase applied), Redis and Kafka")
    void applicationStartsOnRealInfrastructure() throws Exception {
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(500);
        assertThat(redis.getReactiveConnection().ping().block(Duration.ofSeconds(10))).isEqualTo("PONG");
        assertThat(admin.describeCluster().nodes().get(10, TimeUnit.SECONDS)).isNotEmpty();
        // event_bus.processed_events (yow-event-kernel changeset 007) answers: Liquibase ran on PostGIS
        assertThat(processedEvents.isProcessed("l0", "l0", UUID.randomUUID().toString()).block(Duration.ofSeconds(10)))
            .isFalse();
    }

    @Test
    @DisplayName("the 5 aggregate topics and tnt.dlq exist on the broker; aggregate topics: delete policy, 30 days")
    void aggregateTopicsAreDeclared() throws Exception {
        List<String> aggregateTopics = List.of(TntTopics.PARCEL_EVENTS, TntTopics.DELIVERY_EVENTS,
            TntTopics.INCIDENT_EVENTS, TntTopics.DISPUTE_EVENTS, TntTopics.ANNOUNCEMENT_EVENTS);
        List<String> all = new ArrayList<>(aggregateTopics);
        all.add(TntTopics.DLQ);
        Map<String, TopicDescription> described = admin.describeTopics(all).allTopicNames().get(30, TimeUnit.SECONDS);
        assertThat(described).containsOnlyKeys(all);
        described.values().forEach(d -> assertThat(d.partitions()).hasSize(3));

        List<ConfigResource> resources = aggregateTopics.stream()
            .map(t -> new ConfigResource(ConfigResource.Type.TOPIC, t)).toList();
        Map<ConfigResource, Config> configs = admin.describeConfigs(resources).all().get(30, TimeUnit.SECONDS);
        configs.forEach((resource, config) -> {
            assertThat(config.get(TopicConfig.CLEANUP_POLICY_CONFIG).value()).as(resource.name()).isEqualTo("delete");
            assertThat(config.get(TopicConfig.RETENTION_MS_CONFIG).value()).as(resource.name()).isEqualTo("2592000000");
        });
    }

    @Test
    @DisplayName("every listener container factory carries the common retry + tnt.dlq handler, except link "
            + "(sync joined it at lot L8)")
    void everyFactoryDeadLettersToTheCommonTopic() {
        Set<String> ownHandler = Set.of("linkKafkaListenerContainerFactory");
        List<String> missing = new ArrayList<>();
        listenerFactories.forEach((name, factory) -> {
            Object handler = ReflectionTestUtils.getField(factory, "commonErrorHandler");
            if (!ownHandler.contains(name) && !(handler instanceof DefaultErrorHandler)) {
                missing.add(name + " -> " + handler);
            }
        });
        assertThat(listenerFactories).containsKey("kafkaListenerContainerFactory");
        assertThat(missing).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 2. Aggregate event: outbox -> broker -> blocking listener -> effect
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("aggregate event published through the outbox reaches the blocking listener: schema, key, headers, effect")
    void aggregateEventEndToEnd() throws Exception {
        TntAggregateEvent<Map<String, Object>> event = parcelEvent(TRIGGER_OK);
        probe.expect(event.aggregateId());

        publishEvent.publish(envelopeOf(event)).block(Duration.ofSeconds(30));

        // effect of the listener's reactive work (it ends 300 ms after the record arrived)
        await().atMost(60, TimeUnit.SECONDS).until(() -> probe.processed.containsKey(event.aggregateId()));
        ConsumerRecord<String, byte[]> record = probe.processed.get(event.aggregateId());
        String body = new String(record.value(), StandardCharsets.UTF_8);

        assertThat(record.topic()).isEqualTo(TntTopics.PARCEL_EVENTS);
        assertThat(record.key()).isEqualTo(event.aggregateId());
        assertThat(header(record, "X-Yow-Event-Id")).isEqualTo(event.eventId().toString());
        assertThat(header(record, "X-Yow-Envelope-Id")).isEqualTo(event.eventId().toString());
        assertThat(header(record, "X-Yow-Event-Type")).isEqualTo("ParcelPickedUp");
        assertThat(header(record, "X-Yow-Aggregate-Type")).isEqualTo("PARCEL");
        assertThat(header(record, "X-Yow-Aggregate-Id")).isEqualTo(event.aggregateId());
        assertThat(header(record, "X-Yow-Schema-Version")).isEqualTo("1");
        assertThat(header(record, "X-Yow-Tenant-Id")).isEqualTo(SYSTEM_TENANT);
        assertThat(validate(body)).isEmpty();
        JsonNode json = MAPPER.readTree(body);
        assertThat(json.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(json.get("previousState").get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(TntPiiSentinels.leaks(body)).isEmpty();
        assertThat(probe.attempts(event.aggregateId())).isEqualTo(1);

        // the duplicate filter marked the event processed — after the listener returned, i.e. after the work
        await().atMost(30, TimeUnit.SECONDS).until(() -> Boolean.TRUE.equals(processedEvents
            .isProcessed(PROBE_GROUP, TntTopics.PARCEL_EVENTS, event.eventId().toString()).block()));
    }

    @Test
    @DisplayName("a failing listener work is retried (4 attempts) then dead-lettered to tnt.dlq — record relayed by the outbox relay")
    void failingListenerIsRetriedThenDeadLettered() throws Exception {
        TntAggregateEvent<Map<String, Object>> event = parcelEvent(TRIGGER_FAILURE);
        probe.expect(event.aggregateId());
        // persisted but never sent: the outbox relay takes it over (production through outbox + relay)
        envelopes.save(envelopeOf(event)).block(Duration.ofSeconds(30));

        ConsumerRecord<String, String> dead = awaitRecord(TntTopics.DLQ, r -> event.aggregateId().equals(r.key()));
        assertThat(probe.attempts(event.aggregateId())).isEqualTo(4);
        assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo(TntTopics.PARCEL_EVENTS);
        assertThat(header(dead, "kafka_dlt-original-consumer-group")).isEqualTo(PROBE_GROUP);
        assertThat(header(dead, "X-Yow-Event-Id")).isEqualTo(event.eventId().toString());
        assertThat(header(dead, "kafka_dlt-exception-message") + header(dead, "kafka_dlt-exception-stacktrace"))
            .contains("probe failure");
        assertThat(validate(dead.value())).isEmpty();
        assertThat(processedEvents.isProcessed(PROBE_GROUP, TntTopics.PARCEL_EVENTS, event.eventId().toString())
            .block(Duration.ofSeconds(10))).isFalse();
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(envelopes
            .findById(EnvelopeId.of(event.eventId().toString()), null).block().getStatus())
            .isEqualTo(EnvelopeStatus.PUBLISHED));
    }

    @Test
    @DisplayName("a listener work exceeding tnt.kafka.listener.timeout fails the record: retried, then tnt.dlq")
    void timedOutListenerIsDeadLettered() throws Exception {
        TntAggregateEvent<Map<String, Object>> event = parcelEvent(TRIGGER_TIMEOUT);
        probe.expect(event.aggregateId());
        publishEvent.publish(envelopeOf(event)).block(Duration.ofSeconds(30));

        ConsumerRecord<String, String> dead = awaitRecord(TntTopics.DLQ, r -> event.aggregateId().equals(r.key()));
        assertThat(probe.attempts(event.aggregateId())).isEqualTo(4);
        String exceptions = header(dead, "kafka_dlt-exception-fqcn") + " " + header(dead, "kafka_dlt-exception-cause-fqcn")
            + " " + header(dead, "kafka_dlt-exception-stacktrace");
        assertThat(exceptions).contains("ListenerTimeoutException");
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 3. Lot L8: one delivery driven by the real services, every effect observed without a mock
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("lot L8: a direct delivery assigned, picked up, in transit, delivered (rows D4, D5, D11) — ordered, valid and "
            + "masked parcel/delivery events; real realtime and sync consumers; nothing dead-lettered")
    void deliveryLifecycleEndToEnd() throws Exception {
        UUID tenant = UUID.fromString(SYSTEM_TENANT);
        UUID sender = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        var parcel = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel.create(
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification(2, 20, 20, 20,
                        false, false, "Box"), tenant, sender, TntEventActor.of(TntEventActor.Type.SENDER, sender));
        var pickup = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress(
                TntPiiSentinels.of("pickupStreet"), null, "Bastos", "Yaoundé", "CM",
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(
                        TntPiiSentinels.EXACT_LATITUDE, TntPiiSentinels.EXACT_LONGITUDE));
        var destination = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress(
                TntPiiSentinels.of("deliveryStreet"), null, "Mvog-Mbi", "Yaoundé", "CM",
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(3.8480, 11.5021));
        var delivery = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.create(tenant, null, sender,
                parcel, pickup, destination,
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo(
                        // first name kept, last name reduced to its initial (spec §3.4): the sentinel must not leak
                        "Awa " + TntPiiSentinels.of("recipientLastName"), "+237699000111", null),
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency.STANDARD, null, null);
        UUID id = delivery.getId();
        String parcelId = parcel.getId().toString();
        Duration wait = Duration.ofSeconds(30);

        // as DeliveryAnnouncementService.selectResponse: D1 and D2 are two saves (one transition per save, so the
        // aggregateVersion strictly increases, spec §3.2-§3.3), then the events of both are published
        var created = deliveries.save(delivery).block(wait);
        created.assignDeliveryPerson(courier, null, 2.0, null);
        deliveryTransitions.saveAndPublish(created).block(wait);
        deliveryLifecycle.confirmPickup(new com.yowyob.tiibntick.core.delivery.application.port.in.command.ConfirmPickupCommand(
                tenant, id, courier)).block(wait);
        deliveryLifecycle.startTransit(new com.yowyob.tiibntick.core.delivery.application.port.in.command.StartTransitCommand(
                tenant, id, courier, null)).block(wait);
        deliveryLifecycle.completeDelivery(new com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand(
                tenant, id, courier, null, null, null, null, null)).block(wait);
        assertThat(deliveries.findById(tenant, id).block(wait).getStatus())
                .isEqualTo(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.DELIVERED);

        // the broker: one ordered stream per aggregate, schema-valid, masked, headers consistent with the body
        List<ConsumerRecord<String, String>> deliveryStream = awaitStream(TntTopics.DELIVERY_EVENTS, id.toString(),
                "DeliveryDelivered");
        List<ConsumerRecord<String, String>> parcelStream = awaitStream(TntTopics.PARCEL_EVENTS, parcelId,
                "ParcelDelivered");
        assertThat(statuses(deliveryStream))
                .containsSubsequence("ASSIGNED", "PICKED_UP", "IN_TRANSIT", "DELIVERED");
        assertThat(statuses(parcelStream)).contains("PICKED_UP").last().isEqualTo("DELIVERED");
        for (ConsumerRecord<String, String> record : concat(deliveryStream, parcelStream)) {
            JsonNode body = MAPPER.readTree(record.value());
            assertThat(validate(record.value())).as(body.path("eventType").asText()).isEmpty();
            assertThat(TntPiiSentinels.leaks(record.value())).as(body.path("eventType").asText()).isEmpty();
            assertThat(record.value()).doesNotContain("+237699000111");
            if (body.path("payload").has("recipientDisplayName")) {
                assertThat(body.path("payload").path("recipientDisplayName").asText()).isEqualTo("Awa P.");
            }
            assertThat(header(record, "X-Yow-Event-Id")).isEqualTo(body.path("eventId").asText());
            assertThat(header(record, "X-Yow-Event-Type")).isEqualTo(body.path("eventType").asText());
        }
        assertThat(versions(deliveryStream)).isSorted().doesNotHaveDuplicates();
        assertThat(versions(parcelStream)).isSorted().doesNotHaveDuplicates();
        String trackingCode = MAPPER.readTree(parcelStream.get(0).value()).path("payload").path("trackingCode").asText();
        assertThat(trackingCode).isEqualTo(parcel.getTrackingCode());

        // real consumers: realtime remembers the parcel code of the delivery; sync indexed the last delivery state
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(trackingCodes.get(id.toString())).isEqualTo(trackingCode);
            var current = entityVersions.findCurrent(SYSTEM_TENANT, "DELIVERY", id.toString()).block(wait);
            assertThat(current).isNotNull();
            assertThat(current.payloadJson()).contains("DELIVERED");
        });

        // no consumer dead-lettered any event of this delivery or of its parcel (DLQ read for 20 s more)
        List<ConsumerRecord<String, String>> dead = readFor(TntTopics.DLQ, Duration.ofSeconds(20),
                r -> id.toString().equals(r.key()) || parcelId.equals(r.key()));
        assertThat(dead).extracting(r -> header(r, "kafka_dlt-original-consumer-group") + ": "
                + header(r, "kafka_dlt-exception-message")).isEmpty();
    }

    private static List<String> statuses(List<ConsumerRecord<String, String>> stream) throws Exception {
        List<String> out = new ArrayList<>();
        for (ConsumerRecord<String, String> record : stream) {
            out.add(MAPPER.readTree(record.value()).path("payload").path("status").asText());
        }
        return out;
    }

    private static List<Long> versions(List<ConsumerRecord<String, String>> stream) throws Exception {
        List<Long> out = new ArrayList<>();
        for (ConsumerRecord<String, String> record : stream) {
            out.add(MAPPER.readTree(record.value()).path("aggregateVersion").asLong());
        }
        return out;
    }

    private static List<ConsumerRecord<String, String>> concat(List<ConsumerRecord<String, String>> a,
                                                               List<ConsumerRecord<String, String>> b) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** Every record of {@code key} on {@code topic}, in partition order, once the one of {@code lastType} arrived. */
    private static List<ConsumerRecord<String, String>> awaitStream(String topic, String key, String lastType) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        Properties props = readerProps();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        out.add(record);
                        if (record.value().contains("\"" + lastType + "\"")) {
                            return out;
                        }
                    }
                }
            }
        }
        throw new AssertionError("no " + lastType + " for " + key + " on " + topic + " within 90s; got " + out.size());
    }

    /** Every record matching {@code match} read from the beginning of {@code topic} during {@code window}. */
    private static List<ConsumerRecord<String, String>> readFor(String topic, Duration window,
                                                                Predicate<ConsumerRecord<String, String>> match) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(readerProps())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + window.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        out.add(record);
                    }
                }
            }
        }
        return out;
    }

    private static Properties readerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("TNT_IT_KAFKA_BOOTSTRAP"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-it-l8-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    /** A parcel event built like a producer will (lot L2): masked snapshot, mechanical type, previous state. */
    private TntAggregateEvent<Map<String, Object>> parcelEvent(String trigger) {
        String parcelId = UUID.randomUUID().toString();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parcelId", parcelId);
        snapshot.put("status", "PICKED_UP");
        snapshot.put("senderDisplayName", TntEventMasking.displayName("Awa Ndiaye"));
        snapshot.put("pickupAddress", masking.address("Bastos", "Yaoundé", "CM",
            TntPiiSentinels.EXACT_LATITUDE, TntPiiSentinels.EXACT_LONGITUDE));
        return TntAggregateEvent.<Map<String, Object>>builder(TntAggregateType.PARCEL)
            .eventTypeFor("PICKED_UP")
            .aggregateId(parcelId)
            .aggregateVersion(3)
            .tenantId(SYSTEM_TENANT)
            .correlationId(UUID.randomUUID().toString())
            .trigger(trigger)
            .actor(TntEventActor.of(TntEventActor.Type.COURIER, UUID.randomUUID()))
            .previousState(TntAggregateEvent.previousState("ASSIGNED", Map.of()))
            .payload(snapshot)
            .build();
    }

    /** Outbox mapping of an aggregate event (body and headers agree; key = aggregate id by default). */
    private static DomainEventEnvelope envelopeOf(TntAggregateEvent<?> event) {
        return DomainEventEnvelope.wrap()
            .eventId(event.eventId().toString())
            // outbox column correlation_id: NOT NULL, VARCHAR(36) -> a UUID, the event id when there is none
            .correlationId(event.correlationId() != null ? event.correlationId() : event.eventId().toString())
            .causationId(event.causationId())
            .eventType(event.eventType())
            .aggregateId(event.aggregateId())
            .aggregateType(event.aggregateType().name())
            .tenantId(event.tenantId())
            .solutionCode("TNT")
            .payload(TntEventJson.write(event))
            .schemaVersion(event.schemaVersion())
            .kafkaTopic(event.topic())
            .occurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
            .build();
    }

    private static List<String> validate(String body) throws Exception {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
            TntApplicationFullStackIT.class.getClassLoader().getResourceAsStream(TntEventSchemas.ENVELOPE),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        return schema.validate(MAPPER.readTree(body)).stream().map(ValidationMessage::getMessage).toList();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static ConsumerRecord<String, String> awaitRecord(String topic, Predicate<ConsumerRecord<String, String>> match) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("TNT_IT_KAFKA_BOOTSTRAP"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-it-l0-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no matching record on topic " + topic + " within 90s");
    }

    // ── probe listener: a real @KafkaListener on Spring Boot's default factory, written like a lot L2+ one ──

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        ProbeListener tntL0ProbeListener(ReactiveListenerSupport listenerSupport) {
            return new ProbeListener(listenerSupport);
        }
    }

    static class ProbeListener {

        final Map<String, ConsumerRecord<String, byte[]>> processed = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final Set<String> expected = ConcurrentHashMap.newKeySet();
        private final ReactiveListenerSupport listenerSupport;

        ProbeListener(ReactiveListenerSupport listenerSupport) {
            this.listenerSupport = listenerSupport;
        }

        void expect(String aggregateId) {
            expected.add(aggregateId);
        }

        int attempts(String aggregateId) {
            AtomicInteger count = attempts.get(aggregateId);
            return count == null ? 0 : count.get();
        }

        @KafkaListener(id = "tntL0SocleProbe", topics = TntTopics.PARCEL_EVENTS, groupId = PROBE_GROUP)
        public void onParcelEvent(ConsumerRecord<String, byte[]> record) throws Exception {
            if (record.key() == null || !expected.contains(record.key())) {
                return;   // records of earlier runs or of other tests
            }
            attempts.computeIfAbsent(record.key(), k -> new AtomicInteger()).incrementAndGet();
            JsonNode body = MAPPER.readTree(record.value());
            if (!TntAggregateEvent.isAggregateEvent(body)) {
                return;
            }
            Mono<Void> work = switch (body.path("trigger").asText()) {
                case TRIGGER_FAILURE -> Mono.delay(Duration.ofMillis(50))
                    .then(Mono.error(new IllegalStateException("probe failure")));
                case TRIGGER_TIMEOUT -> Mono.never();
                default -> Mono.delay(Duration.ofMillis(300)).doOnNext(t -> processed.put(record.key(), record)).then();
            };
            listenerSupport.await(work);
        }
    }
}
