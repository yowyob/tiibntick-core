package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.billing.templates.domain.event.CustomTemplateSavedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.event.TemplateAppliedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link KafkaTemplateEventPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of sending to Kafka directly, and {@code OutboxPollerService}
 * relays them asynchronously — on the same topics and wire payload format (the raw serialised
 * domain event, keyed by {@code ownerActorId}) already consumed downstream (e.g. tnt-notify-core).
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via yow-event-kernel's
 * own Liquibase changelog) and an embedded Kafka broker, mirroring
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest} in tnt-delivery-core (the pilot
 * for this migration pattern).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = KafkaTemplateEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        KafkaTemplateEventPublisherOutboxIntegrationTest.TEMPLATE_APPLIED_TOPIC,
        KafkaTemplateEventPublisherOutboxIntegrationTest.CUSTOM_TEMPLATE_SAVED_TOPIC})
@Tag("integration")
class KafkaTemplateEventPublisherOutboxIntegrationTest {

    static final String TEMPLATE_APPLIED_TOPIC = "tnt.billing.template.applied";
    static final String CUSTOM_TEMPLATE_SAVED_TOPIC = "tnt.billing.custom_template.saved";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_templates_outbox_test")
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
    private KafkaTemplateEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publishTemplateApplied() enqueues to the outbox (PENDING); the poller then "
            + "delivers it keyed by ownerActorId with the raw-event wire format consumers expect")
    void publishTemplateAppliedGoesThroughOutbox() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID tenantId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        String actorId = "actor-" + UUID.randomUUID();
        TemplateAppliedEvent event = TemplateAppliedEvent.builder()
                .templateCode("TPL-FRAGILE")
                .templateName("Fragile goods surcharge")
                .ownerActorId(actorId)
                .ownerType(PolicyOwnerType.AGENCY)
                .createdPolicyId(policyId)
                .tenantId(tenantId.toString())
                .fromCustomTemplate(false)
                .appliedParameters(Map.of("surchargePercent", "12"))
                .build();

        publisher.publishTemplateApplied(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        "TPL-FRAGILE", "PolicyTemplate", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(TEMPLATE_APPLIED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(actorId);
                    assertThat(envelope.getCorrelationId()).isEqualTo(event.getEventId().toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "templates-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, TEMPLATE_APPLIED_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production).
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, TEMPLATE_APPLIED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(TEMPLATE_APPLIED_TOPIC);
            assertThat(received.key()).isEqualTo(actorId);

            // The wire format is the raw serialised domain event — no outbox wrapper.
            JsonNode wireEvent = objectMapper.readTree(received.value());
            assertThat(wireEvent.get("eventType").asText()).isEqualTo("TEMPLATE_APPLIED");
            assertThat(wireEvent.get("templateCode").asText()).isEqualTo("TPL-FRAGILE");
            assertThat(wireEvent.get("createdPolicyId").asText()).isEqualTo(policyId.toString());
            assertThat(wireEvent.get("tenantId").asText()).isEqualTo(tenantId.toString());
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        "TPL-FRAGILE", "PolicyTemplate", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishCustomTemplateSaved() routes to its own topic through the outbox")
    void publishCustomTemplateSavedGoesThroughOutbox() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID customTemplateId = UUID.randomUUID();
        String actorId = "actor-" + UUID.randomUUID();
        CustomTemplateSavedEvent event = CustomTemplateSavedEvent.builder()
                .customTemplateId(customTemplateId)
                .customTemplateName("My fragile preset")
                .ownerActorId(actorId)
                .ownerType(PolicyOwnerType.AGENCY)
                .sourceTemplateCode("TPL-FRAGILE")
                .tenantId(tenantId.toString())
                .build();

        publisher.publishCustomTemplateSaved(event).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        customTemplateId.toString(), "PolicyTemplate", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(CUSTOM_TEMPLATE_SAVED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(actorId);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "templates-outbox-saved-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, CUSTOM_TEMPLATE_SAVED_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, CUSTOM_TEMPLATE_SAVED_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(CUSTOM_TEMPLATE_SAVED_TOPIC);
            assertThat(received.key()).isEqualTo(actorId);
        }
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

        @Bean(name = "billingTemplatesObjectMapper")
        ObjectMapper billingTemplatesObjectMapper() {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
        KafkaTemplateEventPublisher kafkaTemplateEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("billingTemplatesObjectMapper") ObjectMapper billingTemplatesObjectMapper) {
            return new KafkaTemplateEventPublisher(publishEventUseCase, billingTemplatesObjectMapper);
        }
    }
}
