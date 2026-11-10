package com.yowyob.tiibntick.core.billing.report.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceCancelled;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceGenerated;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoicePaid;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.report.adapter.in.kafka.InvoiceEventReportConsumer;
import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.r2dbc.InvoiceReportEntryR2dbcRepository;
import com.yowyob.tiibntick.core.billing.report.application.port.out.BillingKPISnapshotRepository;
import com.yowyob.tiibntick.core.billing.report.application.port.out.ReportExportPort;
import com.yowyob.tiibntick.core.billing.report.application.port.in.query.FreelancerOrgReportQuery;
import com.yowyob.tiibntick.core.billing.report.application.service.InvoiceProjectionService;
import com.yowyob.tiibntick.core.billing.report.application.service.ReportingService;
import com.yowyob.tiibntick.core.billing.report.domain.model.ReportPeriod;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The invoice → report projection against a real Postgres built from the module's own SQL files: the
 * <b>actual</b> event records are serialised the way the outbox publisher does, fed to the Kafka consumer,
 * and the per-issuer report is read back. This is the path that used to produce empty driver reports
 * (issuer columns never written, PAID / CANCELLED never applied).
 */
@SpringBootTest(classes = InvoiceReportProjectionIntegrationTest.TestConfig.class)
@Tag("integration")
class InvoiceReportProjectionIntegrationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final String DRIVER = UUID.randomUUID().toString();
    private static final String OTHER_DRIVER = UUID.randomUUID().toString();
    private static final String MIGRATIONS = "src/main/resources/db/changelog/migrations/";

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_report_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    static {
        POSTGRES.start();
    }

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            for (String file : new String[]{"001_create_billing_report_tables.sql",
                    "002_add_freelancer_org_report_fields.sql"}) {
                st.execute(Files.readString(Path.of(MIGRATIONS + file)));
            }
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        r.add("spring.r2dbc.username", POSTGRES::getUsername);
        r.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired InvoiceEventReportConsumer consumer;
    @Autowired ReportingService reporting;
    @Autowired DatabaseClient db;

    private static final ObjectMapper EVENTS = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private void deliver(Object event) throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("tnt.billing.invoice.events", 0, 0L, "k",
                EVENTS.writeValueAsString(event));
        rec.headers().add(new RecordHeader("eventType",
                event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8)));
        consumer.onInvoiceEvent(rec);
    }

    private InvoiceGenerated generated(UUID id, UUID tenant, String issuer, long amount) {
        return new InvoiceGenerated(id, "INV-" + id.toString().substring(0, 6), "M-" + id,
                Money.xaf(amount), tenant, Instant.now(), "client-1", "CM", "FREELANCER_ORG", issuer, "TPL-NIGHT");
    }

    private ReportPeriod today() {
        return ReportPeriod.of(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
    }

    private String statusOf(UUID id) {
        return db.sql("SELECT status FROM tnt_invoice_report_entries WHERE invoice_id = :id").bind("id", id)
                .map((row, m) -> row.get("status", String.class)).one().block();
    }

    @Test
    @DisplayName("ISSUED then PAID: the driver's report finds the invoice, with issuer, template and PAID status")
    void issuedThenPaidIsReportedForTheDriver() throws Exception {
        UUID id = UUID.randomUUID();
        deliver(generated(id, TENANT, DRIVER, 5000));
        assertThat(statusOf(id)).isEqualTo("ISSUED");

        deliver(new InvoicePaid(id, "PAY-1", Money.xaf(5000), Instant.now(), TENANT));
        assertThat(statusOf(id)).isEqualTo("PAID");

        var report = reporting.generateFreelancerOrgReport(
                new FreelancerOrgReportQuery(TENANT, DRIVER, today(), "XAF")).block();
        assertThat(report.getTotalDeliveries()).isEqualTo(1);
        assertThat(report.getCompletedDeliveries()).isEqualTo(1);
        assertThat(report.getTemplatesApplied()).isEqualTo(1);
        assertThat(report.getTotalRevenue().amount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("CANCELLED is applied, and a late ISSUED replay does not resurrect a PAID invoice")
    void cancelledAppliedAndTerminalStatusNotRegressed() throws Exception {
        UUID cancelled = UUID.randomUUID();
        deliver(generated(cancelled, TENANT, DRIVER, 1000));
        deliver(new InvoiceCancelled(cancelled, "client asked", Instant.now(), TENANT));
        assertThat(statusOf(cancelled)).isEqualTo("CANCELLED");

        UUID paid = UUID.randomUUID();
        deliver(generated(paid, TENANT, DRIVER, 2000));
        deliver(new InvoicePaid(paid, "PAY-2", Money.xaf(2000), Instant.now(), TENANT));
        deliver(generated(paid, TENANT, DRIVER, 2000)); // redelivered
        assertThat(statusOf(paid)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("reports are scoped by tenant and by issuer")
    void reportsAreScoped() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        String issuer = "isolated-" + UUID.randomUUID();
        deliver(generated(mine, TENANT, issuer, 3000));
        deliver(generated(theirs, TENANT, OTHER_DRIVER, 4000));
        deliver(generated(otherTenant, OTHER_TENANT, issuer, 9000));

        var report = reporting.generateFreelancerOrgReport(
                new FreelancerOrgReportQuery(TENANT, issuer, today(), "XAF")).block();
        assertThat(report.getTotalDeliveries()).isEqualTo(1);
        assertThat(report.getTotalRevenue().amount()).isEqualByComparingTo("3000");

        var none = reporting.generateFreelancerOrgReport(
                new FreelancerOrgReportQuery(UUID.randomUUID(), issuer, today(), "XAF")).block();
        assertThat(none.getTotalDeliveries()).isZero();
    }

    @Configuration
    @Import({R2dbcAutoConfiguration.class, DataR2dbcAutoConfiguration.class,
            InvoiceReportProjectionRepositoryAdapter.class, InvoiceProjectionService.class,
            InvoiceEventReportConsumer.class})
    @EnableR2dbcRepositories(basePackageClasses = InvoiceReportEntryR2dbcRepository.class)
    static class TestConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean BillingKPISnapshotRepository kpiRepo() { return mock(BillingKPISnapshotRepository.class); }
        @Bean ReportExportPort exportPort() { return mock(ReportExportPort.class); }
        @Bean ReportingService reportingService(
                com.yowyob.tiibntick.core.billing.report.application.port.out.InvoiceReportProjectionRepository p,
                BillingKPISnapshotRepository k, ReportExportPort e) {
            return new ReportingService(p, k, e);
        }
    }
}
