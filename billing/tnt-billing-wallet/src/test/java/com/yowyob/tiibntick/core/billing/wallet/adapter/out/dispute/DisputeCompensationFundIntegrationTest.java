package com.yowyob.tiibntick.core.billing.wallet.adapter.out.dispute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelSettlementAdapter;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.messaging.CompensationOutboxEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.WalletCompensationRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletCompensationR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.PayCompensationCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.service.CompensationService;
import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBlockchainProofPort;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeNotificationPort;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeParcelPort;
import com.yowyob.tiibntick.core.dispute.application.service.DisputeCommandService;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.CompensationDetails;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.messaging.DisputeAggregateEventOutboxPublisher;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L5 (spec §4.3) on a real PostgreSQL, a real Kafka broker and a stub of the Kernel settlement API: a dispute ruled
 * with compensation is paid by the Kernel fund and becomes COMPENSATED in the same request; the mirror is unique per
 * dispute; {@code tnt.billing.compensation.paid} reaches Kafka; a short fund leaves the dispute PENDING_COMPENSATION
 * until the retry; the mirror survives a rollback of the caller's transaction.
 */
@SpringBootTest(classes = DisputeCompensationFundIntegrationTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {TntTopics.BILLING_COMPENSATION_PAID, TntTopics.DISPUTE_EVENTS})
@Tag("integration")
class DisputeCompensationFundIntegrationTest {

    static final UUID TENANT = UUID.randomUUID();
    static final String CLAIMANT = UUID.randomUUID().toString();
    static final String MEDIATOR = UUID.randomUUID().toString();
    static final UUID FUND_WALLET = UUID.randomUUID();
    static final AtomicReference<BigDecimal> FUND = new AtomicReference<>(new BigDecimal("1000000"));
    static final List<RecordedRequest> CALLS = new CopyOnWriteArrayList<>();
    static final MockWebServer KERNEL = new MockWebServer();

    static PostgreSQLContainer<?> container;
    static String host;
    static int port;
    static String user;
    static String password;
    static String database;

    static synchronized void ensureInfra() throws Exception {
        if (database != null) {
            return;
        }
        KERNEL.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                CALLS.add(r);
                String path = r.getPath();
                if (path.startsWith("/api/payments/wallets/owner/")) {
                    String owner = path.substring(path.lastIndexOf('/') + 1);
                    return ok("{\"id\":\"" + UUID.nameUUIDFromBytes(owner.getBytes()) + "\",\"ownerId\":\"" + owner
                            + "\",\"ownerType\":\"USER\"}");
                }
                if (path.equals("/api/payments/compensation-fund")) {
                    return ok("{\"walletId\":\"" + FUND_WALLET + "\",\"balance\":" + FUND.get() + "}");
                }
                if (path.equals("/api/payments/compensations")) {
                    String body = r.getBody().readUtf8();
                    try {
                        JsonNode b = new ObjectMapper().readTree(body);
                        FUND.set(FUND.get().subtract(b.path("amount").decimalValue()));
                        return ok("{\"id\":\"" + UUID.randomUUID() + "\",\"reference\":\"" + b.path("reference").asText()
                                + "\",\"fundWalletId\":\"" + FUND_WALLET + "\",\"beneficiaryWalletId\":\""
                                + b.path("beneficiaryWalletId").asText() + "\",\"amount\":" + b.path("amount") + "}");
                    } catch (Exception e) {
                        return new MockResponse().setResponseCode(400);
                    }
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        KERNEL.start();
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
                    .withDatabaseName("tnt_wallet_l5_it").withUsername("tnt_test").withPassword("tnt_test_secret");
            container.start();
            host = container.getHost();
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
            user = container.getUsername();
            password = container.getPassword();
            admin = container.getDatabaseName();
        }
        String db = "tnt_wallet_l5_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(url(admin), user, password); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        database = db;
        try (Connection conn = jdbc()) {
            for (String changelog : List.of("db/changelog/yow-event-kernel-master.yaml",
                    "db/changelog/tnt-dispute-master.yaml", "db/changelog/tnt-billing-wallet-master.yaml")) {
                Database d = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                new Liquibase(changelog, new ClassLoaderResourceAccessor(), d).update(new Contexts(),
                        new LabelExpression());
            }
        }
    }

    private static MockResponse ok(String data) {
        return new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"data\":" + data + "}");
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
        ensureInfra();
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + host + ":" + port + "/" + database);
        registry.add("spring.r2dbc.username", () -> user);
        registry.add("spring.r2dbc.password", () -> password);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @AfterAll
    static void shutdown() throws Exception {
        KERNEL.shutdown();
    }

    @Autowired DisputeCommandService disputes;
    @Autowired CompensationService compensations;
    @Autowired ReactiveTransactionManager transactions;
    @Autowired EmbeddedKafkaBroker kafka;

    @Test
    @DisplayName("ruled with compensation → paid by the fund → COMPENSATED; mirror, compensation.paid, idempotent")
    void ruledAndPaid() throws Exception {
        Dispute d = inMediation(DisputeCause.PACKAGE_DAMAGED);
        String id = d.getId().getValue();
        int before = CALLS.size();

        Dispute ruled = disputes.ruleDispute(rule(d, new BigDecimal("15000"))).block();

        assertThat(ruled.getStatus()).isEqualTo(DisputeStatus.COMPENSATED);
        List<RecordedRequest> calls = CALLS.subList(before, CALLS.size());
        assertThat(calls).extracting(RecordedRequest::getPath).containsExactly(
                "/api/payments/wallets/owner/" + CLAIMANT, "/api/payments/compensation-fund",
                "/api/payments/compensations");
        String kernelId;
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_disputes WHERE id = '" + id + "'")).isEqualTo("COMPENSATED");
            kernelId = one(c, "SELECT kernel_compensation_id FROM billing.wallet_compensations WHERE dispute_id = '"
                    + id + "'");
            assertThat(kernelId).isNotNull();
            assertThat(one(c, "SELECT compensation_payment_reference FROM tnt_disputes WHERE id = '" + id + "'"))
                    .isEqualTo(kernelId);
            assertThat(one(c, "SELECT amount FROM billing.wallet_compensations WHERE dispute_id = '" + id + "'"))
                    .startsWith("15000");
        }

        List<ConsumerRecord<String, String>> paid = consume(TntTopics.BILLING_COMPENSATION_PAID, id, 1);
        assertThat(paid).hasSize(1);
        JsonNode p = new ObjectMapper().readTree(paid.get(0).value());
        assertThat(p.path("disputeId").asText()).isEqualTo(id);
        assertThat(p.path("paymentReference").asText()).isEqualTo(kernelId);
        assertThat(p.path("tenantId").asText()).isEqualTo(TENANT.toString());
        assertThat(p.path("beneficiaryActorId").asText()).isEqualTo(CLAIMANT);

        // the dispute consumer receiving that record: acknowledged without a change
        assertThat(disputes.confirmCompensationPaid(new ProcessCompensationCommand(d.getId(), TENANT.toString(),
                kernelId, "SYSTEM")).block().getStatus()).isEqualTo(DisputeStatus.COMPENSATED);
        // a second payment for the same dispute does not reach the Kernel
        int after = CALLS.size();
        compensations.payDisputeCompensation(new PayCompensationCommand(TENANT, id, UUID.fromString(CLAIMANT),
                new BigDecimal("15000"), "XAF")).block();
        assertThat(CALLS.size()).isEqualTo(after);
    }

    @Test
    @DisplayName("fund short: PENDING_COMPENSATION, nothing recorded; refilled: retry pays")
    void fundShortThenRetry() throws Exception {
        FUND.set(new BigDecimal("100"));
        Dispute d = inMediation(DisputeCause.PACKAGE_LOST);
        String id = d.getId().getValue();
        Dispute ruled = disputes.ruleDispute(rule(d, new BigDecimal("20000"))).block();
        assertThat(ruled.getStatus()).isEqualTo(DisputeStatus.PENDING_COMPENSATION);
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM tnt_disputes WHERE id = '" + id + "'"))
                    .isEqualTo("PENDING_COMPENSATION");
            assertThat(one(c, "SELECT count(*) FROM billing.wallet_compensations WHERE dispute_id = '" + id + "'"))
                    .isEqualTo("0");
        }
        assertThatThrownBy(() -> disputes.payCompensation(d.getId(), TENANT.toString()).block())
                .hasMessageContaining("compensation fund holds");

        FUND.set(new BigDecimal("1000000"));
        assertThat(disputes.payCompensation(d.getId(), TENANT.toString()).block().getStatus())
                .isEqualTo(DisputeStatus.COMPENSATED);
        assertThatThrownBy(() -> disputes.payCompensation(d.getId(), TENANT.toString()).block())
                .isInstanceOf(DisputeStateException.class);
    }

    @Test
    @DisplayName("once the Kernel paid, the mirror and its event survive a rollback of the caller's transaction")
    void mirrorSurvivesCallerRollback() throws Exception {
        String disputeId = UUID.randomUUID().toString();
        Mono<Object> caller = compensations.payDisputeCompensation(new PayCompensationCommand(TENANT, disputeId,
                        UUID.fromString(CLAIMANT), new BigDecimal("500"), "XAF"))
                .then(Mono.error(new IllegalStateException("dispute save failed")));
        assertThatThrownBy(() -> TransactionalOperator.create(transactions).transactional(caller).block())
                .hasMessageContaining("dispute save failed");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT count(*) FROM billing.wallet_compensations WHERE dispute_id = '" + disputeId
                    + "'")).isEqualTo("1");
            // the envelope is the durable record (its outbox row is removed once relayed)
            assertThat(one(c, "SELECT count(*) FROM event_bus.domain_event_envelopes WHERE aggregate_type ="
                    + " 'WalletCompensation' AND correlation_id = '" + disputeId + "'")).isEqualTo("1");
        }
        assertThat(consume(TntTopics.BILLING_COMPENSATION_PAID, disputeId, 1)).hasSize(1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    Dispute inMediation(DisputeCause cause) {
        Dispute d = disputes.openDispute(new OpenDisputeCommand(TENANT.toString(), CLAIMANT, ClaimantType.CLIENT,
                UUID.randomUUID().toString(), RespondentType.FREELANCER, cause, DisputeCategory.MISSION_GO,
                DisputePriority.HIGH, null, null, null, "colis", null, null, false)).block();
        disputes.assignMediator(new AssignMediatorCommand(d.getId(), TENANT.toString(), MEDIATOR, MEDIATOR)).block();
        return disputes.startMediation(new StartMediationCommand(d.getId(), TENANT.toString(), MEDIATOR)).block();
    }

    static RuleDisputeCommand rule(Dispute d, BigDecimal amount) {
        return new RuleDisputeCommand(d.getId(), TENANT.toString(), MEDIATOR, ResolutionType.DAMAGE_COMPENSATED,
                true, CompensationDetails.approved(amount, "XAF", CompensationMethod.WALLET_CREDIT, CLAIMANT),
                "abîmé", DisputeResponsibleParty.COURIER);
    }

    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private List<ConsumerRecord<String, String>> consume(String topic, String key, int expected) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(kafka,
                "wallet-l5-it-" + UUID.randomUUID(), true));
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
            "com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository"})
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

        @Bean(name = {"tntObjectMapper", "walletObjectMapper", "disputeObjectMapper"})
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

        // ── billing-wallet, real, against the Kernel stub ───────────────────
        @Bean
        KernelSettlementAdapter kernelSettlement() {
            String base = KERNEL.url("/").toString();
            return new KernelSettlementAdapter(WebClient.builder().baseUrl(base).build(),
                    WebClient.builder().baseUrl(base).build());
        }

        @Bean
        WalletCompensationRepositoryAdapter compensationMirrors(WalletCompensationR2dbcRepository repo) {
            return new WalletCompensationRepositoryAdapter(repo);
        }

        @Bean
        CompensationOutboxEventPublisher compensationEvents(PublishEventUseCase publish,
                                                            @Qualifier("walletObjectMapper") ObjectMapper mapper) {
            return new CompensationOutboxEventPublisher(publish, mapper);
        }

        @Bean
        CompensationService compensationService(KernelSettlementAdapter kernel,
                                                WalletCompensationRepositoryAdapter mirrors,
                                                CompensationOutboxEventPublisher events,
                                                ObjectProvider<ReactiveTransactionManager> tx) {
            return new CompensationService(kernel, mirrors, events, tx);
        }

        @Bean
        KernelDisputeCompensationAdapter disputeCompensation(CompensationService service) {
            return new KernelDisputeCompensationAdapter(service);
        }

        // ── dispute-core, real except notifications and parcels (no parcel on these disputes) ──
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
        DisputeCommandService disputeCommandService(DisputeRepositoryAdapter repository,
                                                    KernelDisputeCompensationAdapter fund,
                                                    DisputeReferenceSequenceAdapter references) {
            IDisputeParcelPort noParcel = mock(IDisputeParcelPort.class);
            when(noParcel.resolveParcel(any(), any(), any())).thenReturn(Mono.empty());
            when(noParcel.openDispute(anyString(), anyString(), anyString(), anyBoolean(), any(TntEventActor.class)))
                    .thenReturn(Mono.empty());
            when(noParcel.applyOutcome(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
            return new DisputeCommandService(repository, mock(IDisputeNotificationPort.class,
                    i -> Mono.empty()), noParcel, fund, mock(IBlockchainProofPort.class), references);
        }
    }
}
