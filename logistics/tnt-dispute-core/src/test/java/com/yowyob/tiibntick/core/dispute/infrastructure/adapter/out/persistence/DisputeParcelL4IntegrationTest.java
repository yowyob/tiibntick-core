package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.adapter.out.messaging.KafkaDeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.DeliveryAnnouncementRepositoryAdapter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.DeliveryPersonRepositoryAdapter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.DeliveryRepositoryAdapter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.ParcelRepositoryAdapter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.ParcelWriter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.*;
import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DisputeFilingWindowPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.RelayOwnershipPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.application.service.ParcelDisputeService;
import com.yowyob.tiibntick.core.delivery.application.service.ReturnDeliveryService;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.*;
import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.application.service.DisputeCommandService;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeCompensationException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.delivery.CoreParcelDisputeAdapter;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.messaging.DisputeAggregateEventOutboxPublisher;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L4 on a real PostGIS and a real Kafka broker, with the real delivery-core parcel service: a dispute and its parcel
 * move in the same transaction (P12 at LT1, P13–P17 at the outcome), a refused parcel leaves no dispute nor event,
 * every dispute change reaches {@code tnt.dispute.events} through the outbox with consecutive versions, the fund being
 * absent leaves the dispute PENDING_COMPENSATION, and the dispute persistence now updates rows and keeps evidences.
 *
 * <p>When {@code TNT_IT_DB_PORT} is set, a running PostGIS is used ({@code TNT_IT_DB_HOST/NAME/USER/PASSWORD}) with a
 * throw-away database; otherwise a Testcontainers {@code postgis/postgis:18-3.6} is started.
 */
@SpringBootTest(classes = DisputeParcelL4IntegrationTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {TntTopics.DISPUTE_EVENTS, TntTopics.PARCEL_EVENTS, TntTopics.DELIVERY_EVENTS})
@Tag("integration")
class DisputeParcelL4IntegrationTest {

    static final UUID TENANT = UUID.randomUUID();
    static final String LEGACY_ID = UUID.randomUUID().toString();
    static final AtomicReference<String> FUND = new AtomicReference<>("UNAVAILABLE");

    static PostgreSQLContainer<?> container;
    static String host;
    static int port;
    static String user;
    static String password;
    static String database;

    static synchronized void ensureDatabase() throws Exception {
        if (database != null) {
            return;
        }
        String externalPort = System.getenv("TNT_IT_DB_PORT");
        String admin;
        if (externalPort != null && !externalPort.isBlank()) {
            host = env("TNT_IT_DB_HOST", "localhost");
            port = Integer.parseInt(externalPort.trim());
            user = env("TNT_IT_DB_USER", "tiibntick");
            password = env("TNT_IT_DB_PASSWORD", "tiibntick_pass");
            admin = env("TNT_IT_DB_NAME", "tiibntick_core");
        } else {
            container = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("tnt_dispute_l4_it").withUsername("tnt_test").withPassword("tnt_test_secret");
            container.start();
            host = container.getHost();
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
            user = container.getUsername();
            password = container.getPassword();
            admin = container.getDatabaseName();
        }
        String db = "tnt_dispute_l4_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(url(admin), user, password); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        database = db;
        try (Connection conn = jdbc()) {
            update(conn, "db/changelog/yow-event-kernel-master.yaml", new LabelExpression());
            update(conn, "db/changelog/tnt-delivery-master.yaml", new LabelExpression());
            // the dispute schema as before lot L4, with a dispute already closed and one still open
            update(conn, "db/changelog/tnt-dispute-master.yaml", new LabelExpression("!evt-l4"));
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(legacyRow(LEGACY_ID, "DSP-202611-90001", "CLOSED_RESOLVED"));
                st.executeUpdate(legacyRow(UUID.randomUUID().toString(), "DSP-202611-90002", "OPEN"));
            }
            update(conn, "db/changelog/tnt-dispute-master.yaml", new LabelExpression());
        }
    }

    private static String legacyRow(String id, String reference, String status) {
        return "INSERT INTO tnt_disputes (id, tenant_id, reference, cause, category, priority, status, claimant_id,"
                + " claimant_type, respondent_id, respondent_type, description, filed_at, updated_at) VALUES ('" + id
                + "', '" + TENANT + "', '" + reference + "', 'PACKAGE_LOST', 'MISSION_GO', 'HIGH', '" + status
                + "', 'c', 'CLIENT', 'r', 'FREELANCER', 'legacy', now() - interval '3 days',"
                + " now() - interval '1 day')";
    }

    private static void update(Connection conn, String changelog, LabelExpression labels) throws Exception {
        Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        new Liquibase(changelog, new ClassLoaderResourceAccessor(), db).update(new Contexts(), labels);
    }

    static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(url(database), user, password);
    }

    private static String url(String db) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + db;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        ensureDatabase();
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + host + ":" + port + "/" + database);
        registry.add("spring.r2dbc.username", () -> user);
        registry.add("spring.r2dbc.password", () -> password);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired DisputeCommandService disputes;
    @Autowired DisputeRepositoryAdapter repository;
    @Autowired DeliveryTransitionExecutor executor;
    @Autowired EmbeddedKafkaBroker kafka;

    // ── tests ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upgrade: legacy disputes get event_sequence 0, a closed one its closed_at")
    void legacyRowsMigrated() throws Exception {
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT event_sequence FROM tnt_disputes WHERE id = '" + LEGACY_ID + "'")).isEqualTo("0");
            assertThat(one(c, "SELECT closed_at IS NOT NULL FROM tnt_disputes WHERE id = '" + LEGACY_ID + "'"))
                    .isEqualTo("t");
            assertThat(one(c, "SELECT closed_at IS NULL FROM tnt_disputes WHERE reference = 'DSP-202611-90002'"))
                    .as("still open: no closed_at").isEqualTo("t");
        }
    }

    @Test
    @DisplayName("loss compensated: P12 then P14 + D21 in the dispute's transactions, 6 ordered tnt.dispute.events")
    void lossCompensatedEndToEnd() throws Exception {
        FUND.set("PAYS");
        Delivery delivery = deliveryInTransit();
        String parcelId = delivery.getParcel().getId().toString();

        Dispute d = disputes.openDispute(open(DisputeCause.PACKAGE_LOST, null, delivery.getId().toString())).block();
        String id = d.getId().getValue();
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT package_id FROM tnt_disputes WHERE id = '" + id + "'")).isEqualTo(parcelId);
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + parcelId + "'")).isEqualTo("UNDER_DISPUTE");
            assertThat(one(c, "SELECT open_dispute_id FROM tnt_parcels WHERE id = '" + parcelId + "'")).isEqualTo(id);
            assertThat(one(c, "SELECT dispute_freezes_delivery FROM tnt_parcels WHERE id = '" + parcelId + "'"))
                    .isEqualTo("t");
        }

        disputes.assignMediator(new AssignMediatorCommand(d.getId(), TENANT.toString(), MEDIATOR, MEDIATOR)).block();
        Dispute withEvidence = repository.findByIdAndTenantId(d.getId(), TENANT.toString())
                .flatMap(x -> {
                    x.addEvidence(DisputeEvidence.create(x.getId(), CLAIMANT, EvidenceSubmitterType.CLAIMANT,
                            EvidenceType.PHOTO, "disputes/a.jpg", "photo", "h1"));
                    return repository.save(x);
                }).block();
        assertThat(withEvidence.getEvidences()).hasSize(1);
        disputes.startMediation(new StartMediationCommand(d.getId(), TENANT.toString(), MEDIATOR)).block();
        Dispute ruled = disputes.ruleDispute(new RuleDisputeCommand(d.getId(), TENANT.toString(), MEDIATOR,
                ResolutionType.TOTAL_LOSS_COMPENSATED, true, CompensationDetails.approved(new BigDecimal("15000.00"),
                "XAF", CompensationMethod.WALLET_CREDIT, CLAIMANT), "perte avérée", DisputeResponsibleParty.COURIER))
                .block();

        assertThat(ruled.getStatus()).isEqualTo(DisputeStatus.COMPENSATED);
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_disputes WHERE id = '" + id + "'")).isEqualTo("COMPENSATED");
            assertThat(one(c, "SELECT responsible_party FROM tnt_disputes WHERE id = '" + id + "'")).isEqualTo("COURIER");
            assertThat(one(c, "SELECT compensation_payment_reference FROM tnt_disputes WHERE id = '" + id + "'"))
                    .isEqualTo("KRN-" + id);
            assertThat(one(c, "SELECT closed_at IS NOT NULL FROM tnt_disputes WHERE id = '" + id + "'")).isEqualTo("t");
            assertThat(one(c, "SELECT event_sequence FROM tnt_disputes WHERE id = '" + id + "'")).isEqualTo("6");
            assertThat(one(c, "SELECT count(*) FROM tnt_dispute_evidences WHERE dispute_id = '" + id + "'"))
                    .isEqualTo("1");
            assertThat(Integer.parseInt(one(c, "SELECT count(*) FROM tnt_dispute_events WHERE dispute_id = '" + id
                    + "'"))).as("timeline persisted").isGreaterThanOrEqualTo(5);
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + parcelId + "'")).isEqualTo("LOST");
            assertThat(one(c, "SELECT open_dispute_id FROM tnt_parcels WHERE id = '" + parcelId + "'")).isNull();
            assertThat(one(c, "SELECT status FROM tnt_deliveries WHERE id = '" + delivery.getId() + "'"))
                    .as("D21").isEqualTo("FAILED");
        }

        List<ConsumerRecord<String, String>> records = consume(TntTopics.DISPUTE_EVENTS, id, 6);
        ObjectMapper json = new ObjectMapper();
        List<String> types = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            JsonNode r = json.readTree(records.get(i).value());
            types.add(r.path("eventType").asText());
            assertThat(r.path("aggregateVersion").asLong()).isEqualTo(i + 1);
            assertThat(r.path("payload").path("parcelId").asText()).isEqualTo(parcelId);
            assertThat(records.get(i).value()).doesNotContain("Dupont", "perte avérée");
        }
        assertThat(types).containsExactly("DisputeOpen", "DisputeUnderInvestigation", "DisputeEvidenceAdded",
                "DisputeMediationInProgress", "DisputePendingCompensation", "DisputeCompensated");
        List<String> parcelEvents = consume(TntTopics.PARCEL_EVENTS, parcelId, 3).stream()
                .map(r -> read(json, r.value()).path("eventType").asText()).toList();
        assertThat(parcelEvents).contains("ParcelUnderDispute", "ParcelLost");
    }

    @Test
    @DisplayName("fund absent: PENDING_COMPENSATION kept with the parcel still UNDER_DISPUTE; retry pays → DAMAGED")
    void fundAbsentThenPaid() throws Exception {
        FUND.set("UNAVAILABLE");
        Delivery delivery = deliveryInTransit();
        String parcelId = delivery.getParcel().getId().toString();
        Dispute d = disputes.openDispute(open(DisputeCause.PACKAGE_DAMAGED, parcelId, null)).block();
        disputes.assignMediator(new AssignMediatorCommand(d.getId(), TENANT.toString(), MEDIATOR, MEDIATOR)).block();
        disputes.startMediation(new StartMediationCommand(d.getId(), TENANT.toString(), MEDIATOR)).block();
        Dispute ruled = disputes.ruleDispute(new RuleDisputeCommand(d.getId(), TENANT.toString(), MEDIATOR,
                ResolutionType.DAMAGE_COMPENSATED, true, CompensationDetails.approved(new BigDecimal("2000"), "XAF",
                CompensationMethod.WALLET_CREDIT, CLAIMANT), "abîmé", DisputeResponsibleParty.COURIER)).block();

        assertThat(ruled.getStatus()).isEqualTo(DisputeStatus.PENDING_COMPENSATION);
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_disputes WHERE id = '" + d.getId().getValue() + "'"))
                    .isEqualTo("PENDING_COMPENSATION");
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + parcelId + "'")).isEqualTo("UNDER_DISPUTE");
        }
        assertThatThrownBy(() -> disputes.payCompensation(d.getId(), TENANT.toString()).block())
                .isInstanceOf(DisputeCompensationException.class);
        assertThatThrownBy(() -> disputes.closeDispute(new CloseDisputeCommand(d.getId(), TENANT.toString(), MEDIATOR,
                ClosureType.ADMINISTRATIVE_CLOSURE, "doublon")).block()).isInstanceOf(DisputeStateException.class);

        FUND.set("PAYS");
        assertThat(disputes.payCompensation(d.getId(), TENANT.toString()).block().getStatus())
                .isEqualTo(DisputeStatus.COMPENSATED);
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + parcelId + "'")).isEqualTo("DAMAGED");
        }
    }

    @Test
    @DisplayName("withdrawal restores the parcel; a closed parcel outside the window refuses: no dispute, no event")
    void withdrawalAndFilingWindow() throws Exception {
        Delivery delivery = deliveryInTransit();
        String parcelId = delivery.getParcel().getId().toString();
        Dispute d = disputes.openDispute(open(DisputeCause.DELIVERY_DELAYED, parcelId, null)).block();
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT dispute_freezes_delivery FROM tnt_parcels WHERE id = '" + parcelId + "'"))
                    .as("QO-7: a delay does not freeze").isEqualTo("f");
        }
        disputes.withdrawDispute(new WithdrawDisputeCommand(d.getId(), TENANT.toString(), CLAIMANT)).block();
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + parcelId + "'")).isEqualTo("IN_TRANSIT");
            assertThat(one(c, "SELECT open_dispute_id FROM tnt_parcels WHERE id = '" + parcelId + "'")).isNull();
            assertThat(one(c, "SELECT status FROM tnt_disputes WHERE id = '" + d.getId().getValue() + "'"))
                    .isEqualTo("CLOSED_WITHDRAWN");
        }

        // a delivered parcel, closed 10 days ago (window 7 days) then 2 days ago
        Delivery old = deliveryInTransit();
        String oldParcel = old.getParcel().getId().toString();
        try (Connection c = jdbc(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE tnt_parcels SET status = 'DELIVERED', status_changed_at = now() - interval '10 days'"
                    + " WHERE id = '" + oldParcel + "'");
        }
        long outboxBefore = outboxRows();
        assertThatThrownBy(() -> disputes.openDispute(open(DisputeCause.PACKAGE_DAMAGED, oldParcel, null)).block())
                .isInstanceOf(DisputeStateException.class).hasMessageContaining("filing window");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT count(*) FROM tnt_disputes WHERE package_id = '" + oldParcel + "'")).isEqualTo("0");
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + oldParcel + "'")).isEqualTo("DELIVERED");
        }
        assertThat(outboxRows()).as("rolled back: no event").isEqualTo(outboxBefore);

        try (Connection c = jdbc(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE tnt_parcels SET status_changed_at = now() - interval '2 days' WHERE id = '"
                    + oldParcel + "'");
        }
        Dispute late = disputes.openDispute(open(DisputeCause.PACKAGE_DAMAGED, oldParcel, null)).block();
        assertThat(late.getStatus()).isEqualTo(DisputeStatus.OPEN);
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + oldParcel + "'")).isEqualTo("UNDER_DISPUTE");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    static final String CLAIMANT = UUID.randomUUID().toString();
    static final String MEDIATOR = UUID.randomUUID().toString();

    static OpenDisputeCommand open(DisputeCause cause, String parcelId, String deliveryId) {
        return new OpenDisputeCommand(TENANT.toString(), CLAIMANT, ClaimantType.CLIENT, UUID.randomUUID().toString(),
                RespondentType.FREELANCER, cause, DisputeCategory.MISSION_GO, DisputePriority.HIGH, deliveryId,
                parcelId, null, "Le client M. Dupont signale un problème", null, null, false);
    }

    Delivery deliveryInTransit() {
        UUID sender = UUID.fromString(CLAIMANT);
        Parcel parcel = Parcel.create(new PackageSpecification(2, 20, 20, 20, false, false, "Box"), TENANT, sender,
                TntEventActor.of(TntEventActor.Type.SENDER, sender));
        Delivery delivery = Delivery.create(TENANT, null, sender, parcel, address("Mokolo"), address("Bastos"),
                new RecipientInfo("Awa Ndiaye", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        delivery.assignDeliveryPerson(UUID.randomUUID(), null, 3.0, null);
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
        return executor.saveAndPublish(delivery).block();
    }

    static DeliveryAddress address(String district) {
        return new DeliveryAddress("Rue 1", null, district, "Yaounde", "CM", new GeoCoordinates(3.87, 11.51));
    }

    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    static long outboxRows() throws SQLException {
        try (Connection c = jdbc()) {
            return Long.parseLong(one(c, "SELECT count(*) FROM event_bus.outbox_entries"));
        }
    }

    private static JsonNode read(ObjectMapper json, String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ConsumerRecord<String, String>> consume(String topic, String key, int expected) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(kafka, "dispute-l4-it-" + UUID.randomUUID(),
                true));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            kafka.consumeFromAnEmbeddedTopic(consumer, topic);
            long deadline = System.currentTimeMillis() + 30_000;
            while (out.size() < expected && System.currentTimeMillis() < deadline) {
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).forEach(r -> {
                    if (key.equals(r.key())) {
                        out.add(r);
                    }
                });
            }
        }
        return out;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class, DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class, YowEventKernelAutoConfiguration.class})
    @EnableR2dbcRepositories(basePackages = {
            "com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence",
            "com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository"})
    static class TestConfig {

        @Bean
        MeterRegistry testMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        @Primary
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean(name = {"tntObjectMapper", "deliveryObjectMapper", "disputeObjectMapper"})
        ObjectMapper moduleObjectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Bean(name = "tntKafkaTemplate")
        KafkaTemplate<String, String> tntKafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> configs = new HashMap<>();
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs));
        }

        // ── delivery-core, real ─────────────────────────────────────────────
        @Bean
        ParcelWriter parcelWriter(R2dbcParcelRepository parcels) {
            return new ParcelWriter(parcels);
        }

        @Bean
        ParcelRepositoryAdapter parcelRepositoryAdapter(R2dbcParcelRepository parcels, ParcelWriter writer) {
            return new ParcelRepositoryAdapter(parcels, writer);
        }

        @Bean
        DeliveryRepositoryAdapter deliveryRepositoryAdapter(R2dbcDeliveryRepository deliveries,
                                                            R2dbcParcelRepository parcels, ParcelWriter writer) {
            return new DeliveryRepositoryAdapter(deliveries, parcels, writer);
        }

        @Bean
        DeliveryAnnouncementRepositoryAdapter announcementRepositoryAdapter(R2dbcAnnouncementRepository rows,
                                                                            R2dbcAnnouncementResponseRepository resp,
                                                                            R2dbcParcelRepository parcels,
                                                                            ParcelWriter writer) {
            return new DeliveryAnnouncementRepositoryAdapter(rows, resp, parcels, writer);
        }

        @Bean
        DeliveryPersonRepositoryAdapter deliveryPersonRepositoryAdapter(R2dbcDeliveryPersonRepository persons) {
            return new DeliveryPersonRepositoryAdapter(persons);
        }

        @Bean
        KafkaDeliveryEventPublisher deliveryEventPublisher(PublishEventUseCase one, PublishEventBatchUseCase batch,
                                                           @Qualifier("deliveryObjectMapper") ObjectMapper mapper,
                                                           ObjectProvider<TntEventMasking> masking) {
            return new KafkaDeliveryEventPublisher(one, batch, masking);
        }

        @Bean
        DeliveryThresholdsPort deliveryThresholds() {
            DeliveryThresholdsPort thresholds = mock(DeliveryThresholdsPort.class);
            when(thresholds.forAgency(any())).thenReturn(Mono.just(new DeliveryThresholdsPort.Thresholds(3, 3, 1)));
            return thresholds;
        }

        @Bean
        ReturnDeliveryService returnDeliveryService(DeliveryAnnouncementRepositoryAdapter announcements,
                                                    DeliveryThresholdsPort thresholds) {
            return new ReturnDeliveryService(announcements, thresholds);
        }

        @Bean
        DeliveryTransitionExecutor deliveryTransitionExecutor(DeliveryRepositoryAdapter deliveries,
                                                              DeliveryAnnouncementRepositoryAdapter announcements,
                                                              KafkaDeliveryEventPublisher events,
                                                              ReturnDeliveryService returns) {
            return new DeliveryTransitionExecutor(deliveries, announcements, events, returns);
        }

        @Bean
        ParcelDisputeService parcelDisputeService(ParcelRepositoryAdapter parcels, DeliveryRepositoryAdapter deliveries,
                                                  DeliveryTransitionExecutor executor, ReturnDeliveryService returns,
                                                  DeliveryThresholdsPort thresholds,
                                                  DeliveryPersonRepositoryAdapter persons,
                                                  ObjectProvider<RelayOwnershipPort> relays) {
            DisputeFilingWindowPort window = tenant -> Mono.just(Duration.ofDays(7));
            return new ParcelDisputeService(parcels, deliveries, executor, returns, thresholds, window, persons, relays);
        }

        @Bean
        ParcelUseCase parcelQuery(ParcelRepositoryAdapter parcels) {
            ParcelUseCase query = mock(ParcelUseCase.class);
            when(query.findParcelById(any())).thenAnswer(i -> parcels.findById(i.getArgument(0)));
            return query;
        }

        // ── dispute-core, real except notifications and the Kernel fund ───────
        @Bean
        DisputeAggregateEventOutboxPublisher disputeAggregatePublisher(PublishEventBatchUseCase batch) {
            return new DisputeAggregateEventOutboxPublisher(batch);
        }

        @Bean
        DisputeRepositoryAdapter disputeRepositoryAdapter(DisputeR2dbcRepository d, DisputeEvidenceR2dbcRepository ev,
                                                          DisputeEventR2dbcRepository t, DisputeCommentR2dbcRepository c,
                                                          DisputeEscalationR2dbcRepository e, ObjectMapper mapper,
                                                          DisputeAggregateEventOutboxPublisher publisher) {
            DisputeRepositoryAdapter adapter = new DisputeRepositoryAdapter(d, ev, t, c, e, mapper);
            adapter.setAggregateEvents(publisher);
            return adapter;
        }

        @Bean
        DisputeReferenceSequenceAdapter disputeReferences(DatabaseClient client) {
            return new DisputeReferenceSequenceAdapter(client);
        }

        @Bean
        CoreParcelDisputeAdapter parcelPort(ParcelDisputeService parcels, ParcelUseCase query) {
            return new CoreParcelDisputeAdapter(parcels, query);
        }

        @Bean
        IDisputeCompensationPort fund() {
            return (tenant, disputeId, beneficiary, amount, currency) -> "PAYS".equals(FUND.get())
                    ? Mono.just("KRN-" + disputeId) : Mono.error(DisputeCompensationException.unavailable());
        }

        @Bean
        DisputeCommandService disputeCommandService(DisputeRepositoryAdapter repository,
                                                    CoreParcelDisputeAdapter parcels,
                                                    IDisputeCompensationPort fund,
                                                    DisputeReferenceSequenceAdapter references) {
            return new DisputeCommandService(repository, mock(IDisputeNotificationPort.class, i -> Mono.empty()),
                    parcels, fund, mock(IBlockchainProofPort.class), references);
        }
    }
}
