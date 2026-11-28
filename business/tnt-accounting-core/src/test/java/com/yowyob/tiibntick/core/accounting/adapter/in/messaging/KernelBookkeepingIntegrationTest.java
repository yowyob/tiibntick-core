package com.yowyob.tiibntick.core.accounting.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.accounting.adapter.out.kernel.KernelBookkeepingAdapter;
import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.kernel.KernelBookkeepingLinkRepositoryAdapter;
import com.yowyob.tiibntick.core.accounting.application.service.KernelBookkeepingService;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.KernelEntryLink;
import com.yowyob.tiibntick.core.accounting.domain.service.BookkeepingFactPolicy;
import com.yowyob.tiibntick.core.accounting.infrastructure.config.AccountingCoreConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
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
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6 (cible §4.6, H.3, QO-20, QO-21) on a real PostgreSQL (accounting changelog), a real Kafka broker and a stub of
 * the Kernel accounting service: the money events reach the real listener, which books each fact once in the right
 * Kernel book (X-Organization-Id), records it in {@code accounting.kernel_entry_links}, and survives a replay, a Kernel
 * outage and a lost answer.
 */
@SpringJUnitConfig(KernelBookkeepingIntegrationTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {TntTopics.BILLING_WALLET_COMMISSION_CALCULATED,
        TntTopics.BILLING_COMPENSATION_PAID, TntTopics.BILLING_INVOICE_PAID})
@Tag("integration")
class KernelBookkeepingIntegrationTest {

    static final UUID PLATFORM_ORG = UUID.randomUUID();
    static final MockWebServer KERNEL = new MockWebServer();
    static final List<RecordedRequest> CALLS = new CopyOnWriteArrayList<>();
    /** Kernel state: accounts and journals per organisation and number/code; entries by id. */
    static final Map<String, String> ACCOUNTS = new ConcurrentHashMap<>();
    static final Map<String, String> JOURNALS = new ConcurrentHashMap<>();
    static final Map<String, JsonNode> ENTRIES = new ConcurrentHashMap<>();
    static final Map<String, String> ENTRY_STATUS = new ConcurrentHashMap<>();
    /** Next POST /entries: 0 = normal, 1 = 503 before creating, 2 = created then 503 (lost answer). */
    static final AtomicInteger ENTRY_FAILURE = new AtomicInteger();
    static final ObjectMapper JSON = new ObjectMapper();

    static PostgreSQLContainer<?> container;
    static String jdbcUrl;
    static String user;
    static String password;
    static DatabaseClient db;

    static synchronized void ensureInfra() throws Exception {
        if (db != null) {
            return;
        }
        KERNEL.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                CALLS.add(r);
                String path = r.getPath();
                String org = r.getHeader("X-Organization-Id");
                try {
                    String body = r.getBody().clone().readUtf8();
                    if (path.startsWith("/api/accounting-service/accounts/search")) {
                        String number = path.substring(path.indexOf("query=") + 6);
                        String id = ACCOUNTS.get(org + ":" + number);
                        return raw(id == null ? "[]"
                                : "[{\"id\":\"" + id + "\",\"accountNumber\":\"" + number + "\"}]");
                    }
                    if (path.equals("/api/accounting-service/accounts") && r.getMethod().equals("POST")) {
                        JsonNode b = JSON.readTree(body);
                        String id = UUID.randomUUID().toString();
                        ACCOUNTS.put(org + ":" + b.path("accountNumber").asText(), id);
                        return raw("{\"id\":\"" + id + "\",\"accountNumber\":\"" + b.path("accountNumber").asText()
                                + "\"}");
                    }
                    if (path.equals("/api/accounting-service/journals")) {
                        if (r.getMethod().equals("GET")) {
                            StringBuilder sb = new StringBuilder("[");
                            JOURNALS.forEach((k, id) -> {
                                if (k.startsWith(org + ":")) {
                                    sb.append(sb.length() > 1 ? "," : "").append("{\"id\":\"").append(id)
                                            .append("\",\"code\":\"").append(k.substring(org.length() + 1))
                                            .append("\"}");
                                }
                            });
                            return raw(sb.append("]").toString());
                        }
                        JsonNode b = JSON.readTree(body);
                        String id = UUID.randomUUID().toString();
                        JOURNALS.put(org + ":" + b.path("code").asText(), id);
                        return raw("{\"id\":\"" + id + "\",\"code\":\"" + b.path("code").asText() + "\"}");
                    }
                    if (path.equals("/api/accounting-service/entries")) {
                        if (r.getMethod().equals("GET")) {
                            StringBuilder sb = new StringBuilder("[");
                            ENTRIES.forEach((id, e) -> {
                                if (org.equals(e.path("org").asText())) {
                                    sb.append(sb.length() > 1 ? "," : "").append("{\"id\":\"").append(id)
                                            .append("\",\"reference\":\"").append(e.path("reference").asText())
                                            .append("\",\"status\":\"").append(ENTRY_STATUS.get(id)).append("\"}");
                                }
                            });
                            return raw(sb.append("]").toString());
                        }
                        int failure = ENTRY_FAILURE.getAndSet(0);
                        if (failure == 1) {
                            return new MockResponse().setResponseCode(503);
                        }
                        String id = UUID.randomUUID().toString();
                        ObjectNode stored = (ObjectNode) JSON.readTree(body);
                        stored.put("org", org);
                        ENTRIES.put(id, stored);
                        ENTRY_STATUS.put(id, "DRAFT");
                        if (failure == 2) {
                            return new MockResponse().setResponseCode(503);
                        }
                        // this one answers inside the Kernel {success, data} wrapper
                        return raw("{\"success\":true,\"data\":{\"id\":\"" + id + "\",\"status\":\"DRAFT\"}}");
                    }
                    if (path.startsWith("/api/accounting-service/entries/") && path.endsWith("/validate")) {
                        String id = path.split("/")[4];
                        ENTRY_STATUS.put(id, "VALIDATED");
                        return raw("{\"id\":\"" + id + "\",\"status\":\"VALIDATED\"}");
                    }
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(500);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        KERNEL.start();
        String externalPort = System.getenv("TNT_IT_DB_PORT");
        String host;
        int port;
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
                    .withDatabaseName("tnt_accounting_l6_it").withUsername("tnt_test").withPassword("tnt_test_secret");
            container.start();
            host = container.getHost();
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
            user = container.getUsername();
            password = container.getPassword();
            admin = container.getDatabaseName();
        }
        String database = "tnt_accounting_l6_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + admin, user,
                password); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + database);
        }
        jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            Database d = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-accounting-master.yaml", new ClassLoaderResourceAccessor(), d)
                    .update(new Contexts(), new LabelExpression());
        }
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .build()));
    }

    static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    static MockResponse raw(String json) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(json);
    }

    @AfterAll
    static void shutdown() throws Exception {
        KERNEL.shutdown();
        if (container != null) {
            container.stop();
        }
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired KernelBookkeepingService bookkeeping;

    @BeforeEach
    void listening() {
        for (MessageListenerContainer c : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(c, broker.getPartitionsPerTopic());
        }
    }

    static String one(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    static void await(String sql, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = one(sql);
            if (expected.equals(last)) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(last).as(sql).isEqualTo(expected);
    }

    static long count(String method, String path) {
        return CALLS.stream().filter(r -> r.getMethod().equals(method) && r.getPath().equals(path)).count();
    }

    static String commission(UUID settlement, UUID delivery, String status) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"CommissionCalculated\",\"tenantId\":\""
                + UUID.randomUUID() + "\",\"settlementId\":\"" + settlement + "\",\"referenceType\":\"DELIVERY\","
                + "\"referenceId\":\"" + delivery + "\",\"commission\":\"150.00\",\"amount\":\"3000.00\","
                + "\"currency\":\"XAF\",\"commissionStatus\":\"" + status
                + "\",\"occurredAt\":\"2026-11-27T08:00:00Z\"}";
    }

    static String entryOf(String sourceKey) throws Exception {
        return one("SELECT kernel_entry_id FROM accounting.kernel_entry_links WHERE source_key = '" + sourceKey + "'");
    }

    @Test
    @DisplayName("a SETTLED commission on Kafka → one validated entry D 4671 / C 706 in the platform's Kernel book; replay and a second commission book nothing twice")
    void commissionBookedOnce() throws Exception {
        UUID settlement = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        String event = commission(settlement, delivery, "SETTLED");
        kafka.send(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, delivery.toString(), event).get();
        await("SELECT status || '/' || owner_type || '/' || owner_id || '/' || amount::numeric(19,2) FROM"
                        + " accounting.kernel_entry_links WHERE source_key = 'COMMISSION:" + settlement + "'",
                "VALIDATED/PLATFORM/" + PLATFORM_ORG + "/150.00");
        String entryId = entryOf("COMMISSION:" + settlement);
        JsonNode posted = ENTRIES.get(entryId);
        assertThat(posted.path("org").asText()).isEqualTo(PLATFORM_ORG.toString());
        assertThat(posted.path("reference").asText()).isEqualTo("COM-" + delivery);
        assertThat(ENTRY_STATUS.get(entryId)).isEqualTo("VALIDATED");
        assertThat(posted.path("lines").get(0).path("accountId").asText()).isEqualTo(ACCOUNTS.get(PLATFORM_ORG + ":4671"));
        assertThat(posted.path("lines").get(0).path("debit").decimalValue()).isEqualByComparingTo("150");
        assertThat(posted.path("lines").get(1).path("accountId").asText()).isEqualTo(ACCOUNTS.get(PLATFORM_ORG + ":706"));
        assertThat(posted.path("lines").get(1).path("credit").decimalValue()).isEqualByComparingTo("150");

        long posts = count("POST", "/api/accounting-service/entries");
        long accountCreations = count("POST", "/api/accounting-service/accounts");
        kafka.send(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, delivery.toString(), event).get();
        UUID second = UUID.randomUUID();
        kafka.send(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, delivery.toString(),
                commission(second, UUID.randomUUID(), "SETTLED")).get();
        await("SELECT status FROM accounting.kernel_entry_links WHERE source_key = 'COMMISSION:" + second + "'",
                "VALIDATED");
        assertThat(count("POST", "/api/accounting-service/entries") - posts).as("only the second commission")
                .isEqualTo(1);
        assertThat(count("POST", "/api/accounting-service/accounts")).as("accounts reused")
                .isEqualTo(accountCreations);
        assertThat(one("SELECT count(*) FROM accounting.kernel_entry_links WHERE source_key = 'COMMISSION:"
                + settlement + "'")).isEqualTo("1");
        assertThat(one("SELECT count(*) FROM accounting.journal_entries")).as("QO-21: local journal not written")
                .isEqualTo("0");
    }

    @Test
    @DisplayName("a commission still DUE (cash not remitted) and an invoice without issuer are SKIPPED, nothing sent to the Kernel")
    void skippedFacts() throws Exception {
        UUID settlement = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        long before = CALLS.size();
        kafka.send(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED, "k",
                commission(settlement, UUID.randomUUID(), "DUE")).get();
        await("SELECT status || '/' || reason FROM accounting.kernel_entry_links WHERE source_key = 'COMMISSION:"
                + settlement + "'", "SKIPPED/COMMISSION_NOT_SETTLED");
        kafka.send(TntTopics.BILLING_INVOICE_PAID, invoice.toString(), "{\"invoiceId\":\"" + invoice
                + "\",\"paymentRef\":\"PAY\",\"amount\":{\"amount\":5000,\"currency\":\"XAF\"},\"tenantId\":\""
                + UUID.randomUUID() + "\"}").get();
        await("SELECT status || '/' || reason FROM accounting.kernel_entry_links WHERE source_key = 'INVOICE_PAID:"
                + invoice + "'", "SKIPPED/NO_ISSUER_ORGANIZATION");
        assertThat(CALLS.size()).isEqualTo(before);
    }

    @Test
    @DisplayName("a paid invoice is booked D 4671 / C 411 in the issuing organisation's Kernel book; a return paid by the fund D 6788 / C 4672 in the platform's")
    void invoiceAndReturnCost() throws Exception {
        UUID issuer = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        kafka.send(TntTopics.BILLING_INVOICE_PAID, invoice.toString(), "{\"invoiceId\":\"" + invoice
                + "\",\"paymentRef\":\"PAY\",\"amount\":{\"amount\":5000,\"currency\":\"XAF\"},\"tenantId\":\""
                + UUID.randomUUID() + "\",\"issuerOrgType\":\"AGENCY\",\"issuerOrgId\":\"" + issuer + "\"}").get();
        await("SELECT status || '/' || owner_type || '/' || owner_id FROM accounting.kernel_entry_links WHERE"
                + " source_key = 'INVOICE_PAID:" + invoice + "'", "VALIDATED/ORGANIZATION/" + issuer);
        JsonNode entry = ENTRIES.get(entryOf("INVOICE_PAID:" + invoice));
        assertThat(entry.path("org").asText()).isEqualTo(issuer.toString());
        assertThat(entry.path("lines").get(0).path("accountId").asText()).isEqualTo(ACCOUNTS.get(issuer + ":4671"));
        assertThat(entry.path("lines").get(1).path("accountId").asText()).isEqualTo(ACCOUNTS.get(issuer + ":411"));

        UUID compensation = UUID.randomUUID();
        UUID ret = UUID.randomUUID();
        kafka.send(TntTopics.BILLING_COMPENSATION_PAID, "r", "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"tenantId\":\"" + UUID.randomUUID() + "\",\"reason\":\"RETURN_COST\",\"disputeId\":null,"
                + "\"returnDeliveryId\":\"" + ret + "\",\"compensationId\":\"" + compensation
                + "\",\"amount\":\"2850\",\"currency\":\"XAF\"}").get();
        await("SELECT status || '/' || reference FROM accounting.kernel_entry_links WHERE source_key = 'COMPENSATION:"
                + compensation + "'", "VALIDATED/RET-" + ret);
        JsonNode cost = ENTRIES.get(entryOf("COMPENSATION:" + compensation));
        assertThat(cost.path("lines").get(0).path("accountId").asText())
                .isEqualTo(ACCOUNTS.get(PLATFORM_ORG + ":" + BookkeepingFactPolicy.COMPENSATIONS));
        assertThat(cost.path("lines").get(1).path("accountId").asText())
                .isEqualTo(ACCOUNTS.get(PLATFORM_ORG + ":" + BookkeepingFactPolicy.FUND));
    }

    @Test
    @DisplayName("Kernel down → the listener retries and books; an answer lost after the Kernel created the entry → FAILED, the replay finds it by reference and validates it, never a second entry")
    void outageAndLostAnswer() throws Exception {
        UUID dispute = UUID.randomUUID();
        UUID compensation = UUID.randomUUID();
        ENTRY_FAILURE.set(1);
        kafka.send(TntTopics.BILLING_COMPENSATION_PAID, dispute.toString(), "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"reason\":\"DISPUTE_COMPENSATION\",\"disputeId\":\"" + dispute + "\",\"compensationId\":\""
                + compensation + "\",\"amount\":\"15000\",\"currency\":\"XAF\"}").get();
        // the first delivery fails (503), the container redelivers: booked on the retry
        await("SELECT status FROM accounting.kernel_entry_links WHERE source_key = 'COMPENSATION:" + compensation
                + "'", "VALIDATED");
        assertThat(ENTRIES.get(entryOf("COMPENSATION:" + compensation)).path("reference").asText())
                .isEqualTo("IND-" + dispute);

        UUID lost = UUID.randomUUID();
        ENTRY_FAILURE.set(2);
        long posts = count("POST", "/api/accounting-service/entries");
        KernelEntryLink failed = bookkeeping.book(BookkeepingFactPolicy.compensation(JSON.readTree(
                        "{\"compensationId\":\"" + lost + "\",\"disputeId\":\"d-" + lost + "\",\"amount\":\"100\"}")))
                .onErrorResume(e -> reread(lost)).block();
        assertThat(failed.status()).isEqualTo(KernelEntryLink.Status.FAILED);
        assertThat(failed.reason()).startsWith("KERNEL_UNAVAILABLE");
        assertThat(bookkeeping.list(KernelEntryLink.Status.FAILED, 10).map(KernelEntryLink::id).collectList().block())
                .contains(failed.id());

        KernelEntryLink replayed = bookkeeping.retry(failed.id()).block();
        assertThat(replayed.status()).isEqualTo(KernelEntryLink.Status.VALIDATED);
        assertThat(count("POST", "/api/accounting-service/entries") - posts).as("the lost entry is not posted again")
                .isEqualTo(1);
        assertThat(ENTRY_STATUS.get(replayed.kernelEntryId().toString())).isEqualTo("VALIDATED");
    }

    @Test
    @DisplayName("platform book not configured → FAILED PLATFORM_ORGANIZATION_NOT_CONFIGURED, nothing sent; replayable once set")
    void platformNotConfigured() throws Exception {
        KernelBookkeepingService unconfigured = new KernelBookkeepingService(
                new KernelBookkeepingAdapter(WebClient.builder().baseUrl(KERNEL.url("/").toString()).build()),
                new KernelBookkeepingLinkRepositoryAdapter(db), null, Clock.systemUTC());
        long before = CALLS.size();
        UUID settlement = UUID.randomUUID();
        KernelEntryLink link = unconfigured.book(BookkeepingFactPolicy.commission(JSON.readTree(
                commission(settlement, UUID.randomUUID(), "SETTLED")))).block();
        assertThat(link.status()).isEqualTo(KernelEntryLink.Status.FAILED);
        assertThat(link.reason()).isEqualTo(KernelBookkeepingService.PLATFORM_ORGANIZATION_NOT_CONFIGURED);
        assertThat(CALLS.size()).isEqualTo(before);
        assertThat(bookkeeping.retry(link.id()).block().status()).isEqualTo(KernelEntryLink.Status.VALIDATED);
    }

    static Mono<KernelEntryLink> reread(UUID compensation) {
        return new KernelBookkeepingLinkRepositoryAdapter(db).findBySourceKey("COMPENSATION:" + compensation);
    }

    @Configuration
    @EnableKafka
    static class TestConfig {

        TestConfig() throws Exception {
            ensureInfra();
        }

        @Bean("tntObjectMapper")
        ObjectMapper tntObjectMapper() {
            return new ObjectMapper();
        }

        @Bean
        KernelBookkeepingService kernelBookkeepingService() {
            return new KernelBookkeepingService(
                    new KernelBookkeepingAdapter(WebClient.builder().baseUrl(KERNEL.url("/").toString()).build()),
                    new KernelBookkeepingLinkRepositoryAdapter(db), PLATFORM_ORG, Clock.systemUTC());
        }

        @Bean
        BillingEventAccountingConsumer billingEventAccountingConsumer(KernelBookkeepingService service,
                                                                      ObjectMapper tntObjectMapper,
                                                                      ObjectProvider<ReactiveListenerSupport> support) {
            return new BillingEventAccountingConsumer(service, tntObjectMapper, support);
        }

        @Bean("accountingKafkaConsumerFactory")
        ConsumerFactory<String, String> accountingKafkaConsumerFactory(EmbeddedKafkaBroker broker) {
            return new AccountingCoreConfiguration()
                    .accountingKafkaConsumerFactory(broker.getBrokersAsString(), "tnt-accounting-l6-it");
        }

        @Bean("accountingKafkaListenerContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, String> accountingKafkaListenerContainerFactory(
                ConsumerFactory<String, String> accountingKafkaConsumerFactory) {
            return new AccountingCoreConfiguration()
                    .accountingKafkaListenerContainerFactory(accountingKafkaConsumerFactory);
        }

        @Bean
        KafkaTemplate<String, String> producerTemplate(EmbeddedKafkaBroker broker) {
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        }
    }
}
