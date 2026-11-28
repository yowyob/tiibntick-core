package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelSettlementAdapter;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.messaging.SettlementOutboxEventPublisher;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.PaymentMirrorRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.WalletSettlementRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletSettlementR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.service.CashCollectionService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.EscrowService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.PaymentLifecycleService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.SettlementService;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
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
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5 (spec §4.1, §4.2, §4.5) on a real PostgreSQL, a real Kafka broker and a stub of the Kernel settlement API: an
 * escrow held with the payer's code is mirrored; a {@code DeliveryDelivered} record on {@code tnt.delivery.events}
 * reaches the wallet consumer, which releases it (platform identity) and records the settlement — PAID by ESCROW,
 * commission SETTLED — whose {@code payment.confirmed} reaches Kafka; a dispute freezes then refunds; a cash delivery
 * is collected then remitted by the courier.
 */
@SpringBootTest(classes = EscrowLifecycleIntegrationTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {TntTopics.DELIVERY_EVENTS, TntTopics.DISPUTE_EVENTS,
        TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, TntTopics.BILLING_WALLET_COMMISSION_CALCULATED,
        TntTopics.BILLING_COMPENSATION_PAID})
@Tag("integration")
class EscrowLifecycleIntegrationTest {

    static final UUID TENANT = UUID.randomUUID();
    static final UUID CLIENT = UUID.randomUUID();
    static final UUID COURIER = UUID.randomUUID();
    static final UUID PLATFORM_WALLET = UUID.randomUUID();
    static final UUID FUND_WALLET = UUID.randomUUID();
    static final List<RecordedRequest> CALLS = new CopyOnWriteArrayList<>();
    /** Kernel state of each escrow / collection, keyed by its subject (delivery id). */
    static final Map<String, String> ESCROWS = new ConcurrentHashMap<>();
    static final Map<String, String> COLLECTIONS = new ConcurrentHashMap<>();
    static final MockWebServer KERNEL = new MockWebServer();

    static PostgreSQLContainer<?> container;
    static String host;
    static int port;
    static String user;
    static String password;
    static String database;

    static UUID idOf(String subject) {
        return UUID.nameUUIDFromBytes(("k-" + subject).getBytes());
    }

    static String subjectOf(String path) {
        // /api/payments/escrows/{id}/release → the subject whose id it is
        for (String s : ESCROWS.keySet()) {
            if (path.contains(idOf(s).toString())) return s;
        }
        for (String s : COLLECTIONS.keySet()) {
            if (path.contains(idOf(s).toString())) return s;
        }
        return null;
    }

    static synchronized void ensureInfra() throws Exception {
        if (database != null) {
            return;
        }
        KERNEL.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                CALLS.add(r);
                String path = r.getPath();
                String body = r.getBody().clone().readUtf8();
                try {
                    if (path.startsWith("/api/payments/wallets/owner/")) {
                        String owner = path.substring(path.lastIndexOf('/') + 1);
                        return ok("{\"id\":\"" + UUID.nameUUIDFromBytes(owner.getBytes()) + "\",\"ownerId\":\""
                                + owner + "\",\"ownerType\":\"USER\"}");
                    }
                    if (path.equals("/api/payments/compensation-fund")) {
                        return ok("{\"walletId\":\"" + FUND_WALLET + "\",\"balance\":1000000}");
                    }
                    if (path.equals("/api/payments/compensations")) {
                        JsonNode b = new ObjectMapper().readTree(body);
                        return ok("{\"id\":\"" + UUID.randomUUID() + "\",\"reference\":\""
                                + b.path("reference").asText() + "\",\"fundWalletId\":\"" + FUND_WALLET
                                + "\",\"beneficiaryWalletId\":\"" + b.path("beneficiaryWalletId").asText()
                                + "\",\"amount\":" + b.path("amount") + "}");
                    }
                    if (path.equals("/api/payments/escrows/challenge")) {
                        return ok("{\"challengeToken\":\"chal\",\"expiresInSeconds\":300,\"codePreview\":\"***1\"}");
                    }
                    if (path.startsWith("/api/payments/escrows?")) {
                        String subject = path.substring(path.indexOf("subjectId=") + 10).split("&")[0];
                        if (path.contains("subjectType=RETURN_GUARANTEE")) {
                            subject = "G:" + subject;
                        }
                        return ESCROWS.containsKey(subject) ? ok(escrow(subject)) : new MockResponse().setResponseCode(404);
                    }
                    if (path.equals("/api/payments/escrows")) {
                        JsonNode b = new ObjectMapper().readTree(body);
                        String subject = ("RETURN_GUARANTEE".equals(b.path("subjectType").asText()) ? "G:" : "")
                                + b.path("subjectId").asText();
                        ESCROWS.put(subject, "HELD");
                        return ok(escrow(subject));
                    }
                    if (path.startsWith("/api/payments/escrows/")) {
                        String subject = subjectOf(path);
                        String action = path.substring(path.lastIndexOf('/') + 1);
                        ESCROWS.put(subject, switch (action) {
                            case "release" -> "RELEASED";
                            case "refund" -> "REFUNDED";
                            case "freeze" -> "FROZEN";
                            default -> "HELD";
                        });
                        return ok(escrow(subject));
                    }
                    if (path.equals("/api/payments/cash-collections")) {
                        String subject = new ObjectMapper().readTree(body).path("subjectId").asText();
                        COLLECTIONS.put(subject, "OPEN");
                        return ok(collection(subject));
                    }
                    if (path.startsWith("/api/payments/cash-collections/")) {
                        String subject = subjectOf(path);
                        if (path.endsWith("/remit/challenge")) {
                            return ok("{\"challengeToken\":\"remit\",\"expiresInSeconds\":300}");
                        }
                        COLLECTIONS.put(subject, path.endsWith("/collected") ? "COLLECTED"
                                : path.endsWith("/remit") ? "REMITTED" : path.endsWith("/cancel") ? "CANCELLED"
                                : COLLECTIONS.get(subject));
                        return ok(collection(subject));
                    }
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(500);
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
                    .withDatabaseName("tnt_wallet_escrow_it").withUsername("tnt_test").withPassword("tnt_test_secret");
            container.start();
            host = container.getHost();
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
            user = container.getUsername();
            password = container.getPassword();
            admin = container.getDatabaseName();
        }
        String db = "tnt_wallet_escrow_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(url(admin), user, password); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        database = db;
        try (Connection conn = jdbc()) {
            for (String changelog : List.of("db/changelog/yow-event-kernel-master.yaml",
                    "db/changelog/tnt-billing-wallet-master.yaml")) {
                Database d = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                new Liquibase(changelog, new ClassLoaderResourceAccessor(), d).update(new Contexts(),
                        new LabelExpression());
            }
        }
    }

    static String escrow(String subject) {
        boolean guarantee = subject.startsWith("G:");
        return "{\"id\":\"" + idOf(subject) + "\",\"subjectType\":\"" + (guarantee ? "RETURN_GUARANTEE" : "DELIVERY")
                + "\",\"subjectId\":\"" + (guarantee ? subject.substring(2) : subject) + "\",\"amount\":3000,\"currency\":\"XAF\",\"status\":\"" + ESCROWS.get(subject) + "\"}";
    }

    static String collection(String subject) {
        return "{\"id\":\"" + idOf(subject) + "\",\"subjectType\":\"DELIVERY\",\"subjectId\":\"" + subject
                + "\",\"expectedAmount\":3000,\"collectedAmount\":" + ("OPEN".equals(COLLECTIONS.get(subject))
                ? "null" : "3000") + ",\"amountToRemit\":150,\"currency\":\"XAF\",\"status\":\""
                + COLLECTIONS.get(subject) + "\"}";
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

    @Autowired EscrowService escrows;
    @Autowired com.yowyob.tiibntick.core.billing.wallet.application.service.DebtService debts;
    @Autowired CashCollectionService cash;
    @Autowired @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker kafka;

    @Test
    @DisplayName("held with the payer's code → DeliveryDelivered on Kafka → released, settled PAID by ESCROW, payment.confirmed published")
    void heldThenReleasedByTheDeliveryEvent() throws Exception {
        UUID delivery = UUID.randomUUID();
        PaymentIntent intent = escrowIntent(delivery);
        assertThat(escrows.hold(intent.id(), CLIENT, "chal", "123456").block().status().name()).isEqualTo("HELD");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM billing.wallet_escrows WHERE delivery_id = '" + delivery + "'"))
                    .isEqualTo("HELD");
            assertThat(one(c, "SELECT consumed_at IS NOT NULL FROM billing.wallet_payment_intents WHERE id = '"
                    + intent.id() + "'")).isEqualTo("t");
            assertThat(one(c, "SELECT shares FROM billing.wallet_payment_intents WHERE id = '" + intent.id() + "'"))
                    .contains("\"platform\":150", "\"courier\":2850");
        }
        // the hold carried the shares and the payer's code
        String hold = last("/api/payments/escrows").getBody().readUtf8();
        assertThat(hold).contains("\"role\":\"PLATFORM\",\"walletId\":\"" + PLATFORM_WALLET + "\",\"amount\":150",
                "\"role\":\"COURIER\"", "\"amount\":2850", "\"code\":\"123456\"", "\"orderId\":\"" + intent.id() + "\"");

        kafkaTemplate.send(TntTopics.DELIVERY_EVENTS, delivery.toString(),
                deliveryEvent(delivery, "DeliveryDelivered", "OUT_FOR_DELIVERY", "{}")).get();

        awaitSql("SELECT status FROM billing.wallet_escrows WHERE delivery_id = '" + delivery + "'", "RELEASED");
        // the settlement is recorded right after the mirror turns RELEASED
        awaitSql("SELECT method || '/' || status || '/' || commission_status || '/' || commission_amount::numeric(19,2)"
                + " FROM billing.wallet_settlements WHERE reference_id = '" + delivery + "'", "ESCROW/PAID/SETTLED/150.00");
        List<ConsumerRecord<String, String>> confirmed = consume(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED,
                delivery.toString(), 1);
        assertThat(confirmed).hasSize(1);
        JsonNode p = new ObjectMapper().readTree(confirmed.get(0).value());
        assertThat(p.toString()).contains(delivery.toString());
        assertThat(consume(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, delivery.toString(), 1)).hasSize(1);

        // the same record again: the escrow is not released twice
        int releases = count("/release");
        kafkaTemplate.send(TntTopics.DELIVERY_EVENTS, delivery.toString(),
                deliveryEvent(delivery, "DeliveryDelivered", "OUT_FOR_DELIVERY", "{}")).get();
        Thread.sleep(1500);
        assertThat(count("/release")).isEqualTo(releases);
    }

    @Test
    @DisplayName("DisputeOpen freezes, a compensated dispute refunds; a used intent cannot be paid again")
    void disputeFreezesThenRefunds() throws Exception {
        UUID delivery = UUID.randomUUID();
        UUID dispute = UUID.randomUUID();
        escrows.hold(escrowIntent(delivery).id(), CLIENT, "chal", "1").block();

        kafkaTemplate.send(TntTopics.DISPUTE_EVENTS, dispute.toString(),
                disputeEvent(dispute, delivery, "DisputeOpen", null)).get();
        awaitSql("SELECT status FROM billing.wallet_escrows WHERE delivery_id = '" + delivery + "'", "FROZEN");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT reason FROM billing.wallet_escrows WHERE delivery_id = '" + delivery + "'"))
                    .isEqualTo("DISPUTE:" + dispute);
        }

        kafkaTemplate.send(TntTopics.DISPUTE_EVENTS, dispute.toString(),
                disputeEvent(dispute, delivery, "DisputeCompensated", "TOTAL_LOSS_COMPENSATED")).get();
        awaitSql("SELECT status FROM billing.wallet_escrows WHERE delivery_id = '" + delivery + "'", "REFUNDED");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT count(*) FROM billing.wallet_settlements WHERE reference_id = '" + delivery
                    + "'")).as("a refunded escrow settles nothing").isEqualTo("0");
        }

        assertThatThrownBy(() -> escrows.createIntent(request(delivery, PaymentIntent.Mode.ESCROW)).block())
                .isInstanceOfSatisfying(WalletSettlementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(WalletSettlementException.PAYMENT_INTENT_INVALID));
    }

    @Test
    @DisplayName("cash: opened at the selection, collected at the hand-over (CASH_COLLECTED, commission DUE), remitted by the courier (SETTLED)")
    void cashCollectedThenRemitted() throws Exception {
        UUID delivery = UUID.randomUUID();
        PaymentIntent intent = escrows.createIntent(request(delivery, PaymentIntent.Mode.CASH)).block().intent();
        assertThatThrownBy(() -> cash.open(intent.id(), COURIER).block())
                .isInstanceOfSatisfying(WalletSettlementException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(403));
        assertThat(cash.open(intent.id(), CLIENT).block().status().name()).isEqualTo("OPEN");

        kafkaTemplate.send(TntTopics.DELIVERY_EVENTS, delivery.toString(), deliveryEvent(delivery,
                "DeliveryDelivered", "OUT_FOR_DELIVERY", "{}")).get();
        awaitSql("SELECT status FROM billing.wallet_cash_collections WHERE delivery_id = '" + delivery + "'",
                "COLLECTED");
        awaitSql("SELECT method || '/' || status || '/' || commission_status FROM billing.wallet_settlements"
                + " WHERE reference_id = '" + delivery + "'", "CASH/CASH_COLLECTED/DUE");
        assertThat(cash.outstanding(COURIER).collectList().block()).extracting(c -> c.deliveryId()).contains(delivery);

        assertThatThrownBy(() -> cash.remitChallenge(delivery, CLIENT).block())
                .isInstanceOfSatisfying(WalletSettlementException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(403));
        assertThat(cash.remitChallenge(delivery, COURIER).block().challengeToken()).isEqualTo("remit");
        assertThat(cash.remit(delivery, COURIER, "remit", "4242").block().status().name()).isEqualTo("REMITTED");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT commission_status FROM billing.wallet_settlements WHERE reference_id = '"
                    + delivery + "'")).isEqualTo("SETTLED");
        }
        assertThat(cash.outstanding(COURIER).collectList().block()).extracting(c -> c.deliveryId())
                .doesNotContain(delivery);
    }

    @Test
    @DisplayName("an unused intent is replaced by a new challenge (one row per delivery)")
    void unusedIntentReplaced() throws Exception {
        UUID delivery = UUID.randomUUID();
        UUID first = escrows.createIntent(request(delivery, PaymentIntent.Mode.ESCROW)).block().intent().id();
        UUID second = escrows.createIntent(request(delivery, PaymentIntent.Mode.ESCROW)).block().intent().id();
        assertThat(second).isEqualTo(first);
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT count(*) FROM billing.wallet_payment_intents WHERE delivery_id = '" + delivery
                    + "'")).isEqualTo("1");
        }
    }

    @Test
    @DisplayName("QO-26: DisputeCompensated with the courier at fault → his debt, recovered as a DEBT cash collection to the fund; he repays it")
    void compensationBecomesTheCouriersDebt() throws Exception {
        UUID delivery = UUID.randomUUID();
        UUID dispute = UUID.randomUUID();
        escrows.hold(escrowIntent(delivery).id(), CLIENT, "chal", "1").block();

        kafkaTemplate.send(TntTopics.DISPUTE_EVENTS, dispute.toString(), "{\"schemaVersion\":1,\"eventId\":\""
                + UUID.randomUUID() + "\",\"eventType\":\"DisputeCompensated\",\"aggregateType\":\"DISPUTE\","
                + "\"aggregateId\":\"" + dispute + "\",\"tenantId\":\"" + TENANT + "\",\"payload\":{\"deliveryId\":\""
                + delivery + "\",\"resolutionType\":\"DAMAGE_COMPENSATED\",\"responsibleParty\":\"COURIER\","
                + "\"compensationAmount\":\"15000.00\",\"currency\":\"XAF\"},\"details\":{}}").get();

        String debtSql = "SELECT status || '/' || debtor_type || '/' || debtor_id || '/' || amount::numeric(19,2) || '/'"
                + " || (kernel_collection_id IS NOT NULL) FROM billing.wallet_debts WHERE reference = 'DISPUTE:"
                + dispute + "'";
        awaitSql(debtSql, "OPEN/COURIER/" + COURIER + "/15000.00/true");
        awaitSql("SELECT status FROM billing.wallet_escrows WHERE delivery_id = '" + delivery + "'", "REFUNDED");
        String collection = last("/api/payments/cash-collections").getBody().readUtf8();
        assertThat(collection).contains("\"subjectType\":\"DEBT\"", "\"role\":\"PLATFORM\",\"walletId\":\""
                + FUND_WALLET + "\"", "\"courierWalletId\":\"" + UUID.nameUUIDFromBytes(COURIER.toString().getBytes()));

        UUID debtId;
        try (Connection c = jdbc()) {
            debtId = UUID.fromString(one(c, "SELECT id FROM billing.wallet_debts WHERE reference = 'DISPUTE:" + dispute
                    + "'"));
        }
        assertThat(debts.repayChallenge(debtId, COURIER, null).block().challengeToken()).isEqualTo("remit");
        assertThat(debts.repay(debtId, COURIER, null, "remit", "4242").block().status().name()).isEqualTo("REPAID");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT status FROM billing.wallet_debts WHERE id = '" + debtId + "'")).isEqualTo("REPAID");
        }
    }

    @Test
    @DisplayName("QO-22: a return paid by the fund — confirmed without code, delivered → the fund pays its courier (RETURN:<id>), the PENDING debt of the courier at fault opens")
    void returnPaidByTheFund() throws Exception {
        UUID ret = UUID.randomUUID();
        UUID atFault = UUID.randomUUID();
        PaymentIntent intent = escrows.createIntent(request(ret, PaymentIntent.Mode.FUND)).block().intent();
        assertThat(escrows.confirmFunded(intent.id(), CLIENT).block().consumedAt()).isNotNull();
        debts.recordPending(new com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase.NewDebt(
                TENANT, com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt.DebtorType.COURIER, atFault,
                com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt.Reason.RETURN_COST, "RETURN:" + ret,
                ret, intent.shares().courier(), "XAF")).block();
        int escrowHolds = (int) CALLS.stream().filter(r -> r.getPath().equals("/api/payments/escrows")).count();

        kafkaTemplate.send(TntTopics.DELIVERY_EVENTS, ret.toString(),
                deliveryEvent(ret, "DeliveryDelivered", "OUT_FOR_DELIVERY", "{}")).get();

        awaitSql("SELECT status || '/' || amount::numeric(19,2) FROM billing.wallet_debts WHERE reference = 'RETURN:"
                + ret + "'", "OPEN/2850.00");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT amount::numeric(19,2) || '/' || beneficiary_actor_id FROM"
                    + " billing.wallet_compensations WHERE dispute_id = 'RETURN:" + ret + "'"))
                    .isEqualTo("2850.00/" + COURIER);
        }
        assertThat(last("/api/payments/compensations").getBody().readUtf8()).contains("\"reference\":\"RETURN:" + ret);
        assertThat(CALLS.stream().filter(r -> r.getPath().equals("/api/payments/escrows")).count())
                .as("nothing held from the sender").isEqualTo(escrowHolds);
        // lot L6: published for accounting (reason RETURN_COST, no dispute)
        JsonNode paid = new ObjectMapper().readTree(consume(TntTopics.BILLING_COMPENSATION_PAID, ret.toString(), 1)
                .get(0).value());
        assertThat(paid.toString()).contains("\"reason\":\"RETURN_COST\"", "\"returnDeliveryId\":\"" + ret + "\"",
                "\"disputeId\":null");
    }

    @Test
    @DisplayName("D-152: the return guarantee — a second escrow to the fund, held with the second code; delivery-core's port sees it HELD, captures it (paid to the fund) or releases it (refunded)")
    void returnGuarantee() throws Exception {
        com.yowyob.tiibntick.core.billing.wallet.adapter.out.delivery.KernelReturnGuaranteeAdapter port =
                new com.yowyob.tiibntick.core.billing.wallet.adapter.out.delivery.KernelReturnGuaranteeAdapter(escrows);
        UUID captured = UUID.randomUUID();
        PaymentIntent intent = escrowIntent(captured);
        assertThat(escrows.guaranteeChallenge(intent.id(), CLIENT).block().challengeToken()).isEqualTo("chal");
        String sent = last("/api/payments/escrows/challenge").getBody().readUtf8();
        assertThat(sent).contains("\"subjectType\":\"RETURN_GUARANTEE\"", "\"walletId\":\"" + FUND_WALLET + "\"",
                "\"role\":\"PLATFORM\"");
        assertThat(escrows.holdGuarantee(intent.id(), CLIENT, "chal", "2").block().status().name()).isEqualTo("HELD");
        escrows.hold(intent.id(), CLIENT, "chal", "1").block();
        reactor.test.StepVerifier.create(escrows.holdGuarantee(intent.id(), UUID.randomUUID(), "chal", "2"))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 403).verify();
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT count(*) FROM billing.wallet_escrows WHERE delivery_id = '" + captured + "'"))
                    .isEqualTo("2");
        }

        var request = new com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort.Request(TENANT,
                captured, null, null, CLIENT, UUID.randomUUID(), new BigDecimal("3000"), "XAF", "ORANGE_MONEY");
        assertThat(port.hold(request).block().status().name()).isEqualTo("HELD");
        assertThat(port.capture(request).block().status().name()).isEqualTo("CAPTURED");
        assertThat(port.capture(request).block().status().name()).as("idempotent").isEqualTo("CAPTURED");
        try (Connection c = jdbc()) {
            assertThat(one(c, "SELECT purpose || '/' || status FROM billing.wallet_escrows WHERE delivery_id = '"
                    + captured + "' ORDER BY purpose")).isEqualTo("DELIVERY/HELD");
            assertThat(one(c, "SELECT status FROM billing.wallet_escrows WHERE delivery_id = '" + captured
                    + "' AND purpose = 'RETURN_GUARANTEE'")).isEqualTo("RELEASED");
            assertThat(one(c, "SELECT count(*) FROM billing.wallet_settlements WHERE reference_id = '" + captured + "'"))
                    .as("the capture of a guarantee is not the delivery's settlement").isEqualTo("0");
        }

        UUID collected = UUID.randomUUID();
        PaymentIntent second = escrowIntent(collected);
        escrows.holdGuarantee(second.id(), CLIENT, "chal", "2").block();
        var req2 = new com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort.Request(TENANT,
                collected, null, null, CLIENT, UUID.randomUUID(), new BigDecimal("3000"), "XAF", "ORANGE_MONEY");
        assertThat(port.release(req2).block().status().name()).isEqualTo("RELEASED");
        assertThat(port.hold(new com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort.Request(
                TENANT, UUID.randomUUID(), null, null, CLIENT, null, BigDecimal.TEN, "XAF", "ORANGE_MONEY")).block())
                .isEqualTo(new com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort.Result(
                        com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus.NOT_HANDLED,
                        "GUARANTEE_NOT_CONFIRMED"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    PaymentIntent escrowIntent(UUID delivery) {
        return escrows.createIntent(request(delivery, PaymentIntent.Mode.ESCROW)).block().intent();
    }

    static IEscrowUseCase.IntentRequest request(UUID delivery, PaymentIntent.Mode mode) {
        return new IEscrowUseCase.IntentRequest(TENANT, delivery, UUID.randomUUID(), UUID.randomUUID(), mode,
                new BigDecimal("3000"), "XAF", new BigDecimal("0.05"), List.of(), CLIENT, COURIER, null);
    }

    static String deliveryEvent(UUID delivery, String type, String previous, String details) {
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type
                + "\",\"aggregateType\":\"DELIVERY\",\"aggregateId\":\"" + delivery + "\",\"tenantId\":\"" + TENANT
                + "\",\"previousState\":{\"status\":\"" + previous + "\"},\"payload\":{\"deliveryId\":\"" + delivery
                + "\",\"agreedAmount\":\"3000.00\"},\"details\":" + details + "}";
    }

    static String disputeEvent(UUID dispute, UUID delivery, String type, String resolution) {
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + type
                + "\",\"aggregateType\":\"DISPUTE\",\"aggregateId\":\"" + dispute + "\",\"tenantId\":\"" + TENANT
                + "\",\"payload\":{\"disputeId\":\"" + dispute + "\",\"deliveryId\":\"" + delivery
                + "\",\"resolutionType\":" + (resolution == null ? "null" : "\"" + resolution + "\"")
                + ",\"responsibleParty\":\"PLATFORM\"},\"details\":{}}";
    }

    static void awaitSql(String sql, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = jdbc()) {
                last = one(c, sql);
            }
            if (expected.equals(last)) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(last).as(sql).isEqualTo(expected);
    }

    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    static RecordedRequest last(String path) {
        return CALLS.stream().filter(r -> r.getPath().equals(path)).reduce((a, b) -> b).orElseThrow();
    }

    static int count(String fragment) {
        return (int) CALLS.stream().filter(r -> r.getPath().contains(fragment)).count();
    }

    private List<ConsumerRecord<String, String>> consume(String topic, String key, int expected) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(kafka,
                "wallet-escrow-it-" + UUID.randomUUID(), true));
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
    @EnableKafka
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class, DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class, YowEventKernelAutoConfiguration.class})
    @EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository")
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

        @Bean(name = {"tntObjectMapper", "walletObjectMapper"})
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

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> walletKafkaListenerContainerFactory(
                EmbeddedKafkaBroker broker) {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            ConcurrentKafkaListenerContainerFactory<String, String> f = new ConcurrentKafkaListenerContainerFactory<>();
            f.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
            return f;
        }

        // ── billing-wallet, real, against the Kernel stub ───────────────────
        @Bean
        KernelSettlementAdapter kernelSettlement() {
            String base = KERNEL.url("/").toString();
            return new KernelSettlementAdapter(WebClient.builder().baseUrl(base).build(),
                    WebClient.builder().baseUrl(base).build());
        }

        @Bean
        PaymentMirrorRepositoryAdapter paymentMirrors(DatabaseClient db) {
            return new PaymentMirrorRepositoryAdapter(db);
        }

        @Bean
        SettlementService settlementService(WalletSettlementR2dbcRepository repo, PublishEventUseCase publish,
                                            @Qualifier("walletObjectMapper") ObjectMapper mapper) {
            return new SettlementService(new WalletSettlementRepositoryAdapter(repo),
                    new SettlementOutboxEventPublisher(publish, mapper));
        }

        @Bean
        EscrowService escrowService(KernelSettlementAdapter kernel, PaymentMirrorRepositoryAdapter mirrors,
                                    SettlementService settlements) {
            return new EscrowService(kernel, mirrors, settlements, PLATFORM_WALLET, java.time.Clock.systemUTC());
        }

        @Bean
        CashCollectionService cashCollectionService(KernelSettlementAdapter kernel,
                                                    PaymentMirrorRepositoryAdapter mirrors,
                                                    SettlementService settlements) {
            return new CashCollectionService(kernel, mirrors, settlements);
        }

        @Bean
        com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.WalletCompensationRepositoryAdapter compensationMirrors(
                com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletCompensationR2dbcRepository repo) {
            return new com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.WalletCompensationRepositoryAdapter(repo);
        }

        @Bean
        com.yowyob.tiibntick.core.billing.wallet.application.service.CompensationService compensationService(
                KernelSettlementAdapter kernel,
                com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.WalletCompensationRepositoryAdapter mirrors,
                PublishEventUseCase publish, @Qualifier("walletObjectMapper") ObjectMapper mapper,
                ObjectProvider<org.springframework.transaction.ReactiveTransactionManager> tx) {
            return new com.yowyob.tiibntick.core.billing.wallet.application.service.CompensationService(kernel, mirrors,
                    new com.yowyob.tiibntick.core.billing.wallet.adapter.out.messaging.CompensationOutboxEventPublisher(
                            publish, mapper), tx);
        }

        @Bean
        com.yowyob.tiibntick.core.billing.wallet.application.service.DebtService debtService(
                KernelSettlementAdapter kernel, DatabaseClient db) {
            return new com.yowyob.tiibntick.core.billing.wallet.application.service.DebtService(kernel,
                    new com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.DebtRepositoryAdapter(db));
        }

        @Bean
        PaymentLifecycleService paymentLifecycle(EscrowService escrows, CashCollectionService cash,
                com.yowyob.tiibntick.core.billing.wallet.application.service.CompensationService compensations,
                com.yowyob.tiibntick.core.billing.wallet.application.service.DebtService debts) {
            return new PaymentLifecycleService(escrows, cash, compensations, debts);
        }

        @Bean
        WalletPaymentLifecycleConsumer lifecycleConsumer(PaymentLifecycleService lifecycle,
                                                         @Qualifier("walletObjectMapper") ObjectMapper mapper,
                                                         ObjectProvider<com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport> support) {
            return new WalletPaymentLifecycleConsumer(lifecycle, mapper, support);
        }
    }
}
