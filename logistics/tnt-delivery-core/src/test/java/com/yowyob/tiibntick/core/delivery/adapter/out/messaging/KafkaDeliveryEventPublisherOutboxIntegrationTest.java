package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * <b>Current behaviour (outbox decommissioned 2026-09-18):</b> the event is persisted, then published
 * directly to Kafka, and the envelope ends {@code PUBLISHED} ({@code FAILED} if the broker refuses it).
 * The paragraphs below describe the original outbox migration and are kept for history.
 *
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 pilot migration:
 * {@link KafkaDeliveryEventPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of sending to Kafka directly, and {@code OutboxPollerService}
 * relays them asynchronously — on the exact same topic and wire payload format that
 * {@code tnt-incident-core}/{@code tnt-sync-core}/{@code tnt-realtime-core}/
 * {@code tnt-market-back-core} already consume (see the topic-alignment fix this test
 * supersedes: {@code MissionStatusChangedTopicIntegrationTest}, Audit n°3 P6 / Audit n°5 P-01).
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via
 * {@code yow-event-kernel}'s own Liquibase changelog — same pattern as
 * {@code YowEventKernelIntegrationTest}) and an embedded Kafka broker (no Testcontainers
 * Kafka container — see that class' Javadoc for why this repo standardises on
 * {@code spring-kafka-test} for Kafka-side assertions).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = KafkaDeliveryEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {TntTopics.DELIVERY_EVENTS, TntTopics.PARCEL_EVENTS})
@Tag("integration")
class KafkaDeliveryEventPublisherOutboxIntegrationTest {


    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_delivery_outbox_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @BeforeAll
    static void migrateSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            final Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            final Liquibase liquibase = new Liquibase(
                    "db/changelog/yow-event-kernel-master.yaml",
                    new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    @DynamicPropertySource
    static void dynamicProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired
    private KafkaDeliveryEventPublisher publisher;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("lot L8: a delivery transition is published once, on tnt.delivery.events only (no legacy topic)")
    void transitionGoesToTheAggregateTopicOnly() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        UUID tenantId = UUID.randomUUID();
        Delivery delivery = newDelivery(tenantId);
        delivery.clearDomainEvents();
        delivery.assignDeliveryPerson(UUID.randomUUID(), null, 2.0, null);
        List<DeliveryDomainEvent> deliveryEvents = delivery.getDomainEvents().stream()
                .filter(e -> e.aggregateId().equals(delivery.getId())).toList();

        publisher.publishAll(deliveryEvents).block(Duration.ofSeconds(10));

        List<com.yowyob.kernel.event.domain.model.DomainEventEnvelope> envelopes =
                envelopeRepository.findByAggregateId(delivery.getId().toString(), "DELIVERY", tenantId.toString())
                        .collectList().block(Duration.ofSeconds(10));
        assertThat(envelopes).hasSize(1);
        assertThat(envelopes.get(0).getKafkaTopic()).isEqualTo(TntTopics.DELIVERY_EVENTS);
        assertThat(envelopes.get(0).getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED);

        try (Consumer<String, String> rawConsumer = consumer("delivery-outbox-it")) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, TntTopics.DELIVERY_EVENTS);
            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, TntTopics.DELIVERY_EVENTS, Duration.ofSeconds(15));
            assertThat(received.key()).isEqualTo(delivery.getId().toString());
            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.path("eventType").asText()).isEqualTo("DeliveryAssigned");
            assertThat(wire.path("schemaVersion").asInt()).isPositive();
            assertThat(wire.path("previousState").path("status").asText()).isEqualTo("CREATED");
            assertThat(wire.path("payload").path("status").asText()).isEqualTo("ASSIGNED");
        }
    }

    @Test
    @DisplayName("lot L8: the freelancer-org assignment is DeliveryFreelancerOrgAssigned on tnt.delivery.events")
    void freelancerOrgAssignmentOnTheAggregateTopic() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Delivery delivery = newDelivery(tenantId);
        delivery.clearDomainEvents();
        delivery.assignToFreelancerOrg("org-42", FreelancerRole.OWNER);

        publisher.publishAll(new java.util.ArrayList<>(delivery.getDomainEvents())).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        delivery.getId().toString(), "DELIVERY", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(TntTopics.DELIVERY_EVENTS);
                    assertThat(envelope.getEventType()).isEqualTo("DeliveryFreelancerOrgAssigned");
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED);
                })
                .verifyComplete();
    }

    private Consumer<String, String> consumer(String group) {
        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps(embeddedKafka, group, true));
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
    }

    private static Delivery newDelivery(UUID tenantId) {
        UUID sender = UUID.randomUUID();
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"), tenantId, sender,
                TntEventActor.of(TntEventActor.Type.SENDER, sender));
        return Delivery.create(tenantId, null, sender, parcel,
                new DeliveryAddress(null, "Rue 1", "Mokolo", "Yaoundé", "CM", new GeoCoordinates(3.87, 11.51)),
                new DeliveryAddress(null, "Rue 2", "Bastos", "Yaoundé", "CM", new GeoCoordinates(3.88, 11.52)),
                new RecipientInfo("Jean", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
    }

    @Configuration
    @Import({
            R2dbcAutoConfiguration.class,
            R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class,
            YowEventKernelAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        MeterRegistry testMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean(name = "tntObjectMapper")
        ObjectMapper tntObjectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean(name = "deliveryObjectMapper")
        ObjectMapper deliveryObjectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean(name = "tntKafkaTemplate")
        KafkaTemplate<String, String> tntKafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> configs = new HashMap<>();
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configs);
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        KafkaDeliveryEventPublisher kafkaDeliveryEventPublisher(
                PublishEventUseCase publishEventUseCase,
                PublishEventBatchUseCase publishEventBatchUseCase,
                @Qualifier("deliveryObjectMapper") ObjectMapper deliveryObjectMapper) {
            return new KafkaDeliveryEventPublisher(publishEventUseCase, publishEventBatchUseCase,
                    new org.springframework.beans.factory.support.StaticListableBeanFactory()
                            .getBeanProvider(com.yowyob.tiibntick.common.kafka.TntEventMasking.class));
        }
    }
}
