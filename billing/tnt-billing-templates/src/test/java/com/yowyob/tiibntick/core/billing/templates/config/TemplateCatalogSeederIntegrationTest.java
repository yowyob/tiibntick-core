package com.yowyob.tiibntick.core.billing.templates.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.PolicyTemplateRepositoryAdapter;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.mapper.PolicyTemplateMapper;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.PolicyTemplateR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.repository.TemplateParameterR2dbcRepository;
import com.yowyob.tiibntick.core.billing.templates.application.usecase.ListTemplatesUseCase;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres check of the template catalog: the actual changelog, the startup seeder and the listing
 * the frontend calls ({@code GET /api/v1/billing/templates?ownerType=FREELANCER_ORG}). Guards the "no template
 * shows up in the UI" symptom: a seeder that silently fails, or a listing that filters everything out.
 */
@Testcontainers
@Tag("integration")
class TemplateCatalogSeederIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_templates_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    private static PolicyTemplateRepositoryAdapter repository;
    private static ListTemplatesUseCase listing;

    @BeforeAll
    static void migrateAndWire() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-billing-templates-master.yaml",
                    new ClassLoaderResourceAccessor(), database).update(new Contexts(), new LabelExpression());
        }
        ConnectionFactory cf = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost()).port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName()).username(POSTGRES.getUsername()).password(POSTGRES.getPassword())
                .build());
        R2dbcRepositoryFactory factory = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(cf));
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        repository = new PolicyTemplateRepositoryAdapter(
                factory.getRepository(PolicyTemplateR2dbcRepository.class),
                factory.getRepository(TemplateParameterR2dbcRepository.class),
                new PolicyTemplateMapper(om));
        listing = new ListTemplatesUseCase(repository);
    }

    private static List<PolicyTemplate> forFreelancer() {
        return listing.listForOwnerType(PolicyOwnerType.FREELANCER_ORG).collectList().block(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("an empty catalog is seeded and listed for freelancers with every parameter; a second run changes nothing")
    void seededCatalogIsListedForFreelancers() {
        TemplateCatalogSeeder seeder = new TemplateCatalogSeeder(repository);
        seeder.syncCatalog().block(Duration.ofSeconds(60));

        assertThat(repository.countActive().block()).as("seeded templates").isEqualTo(15L);
        List<PolicyTemplate> offered = forFreelancer();
        assertThat(offered).as("templates offered to a freelancer").extracting(PolicyTemplate::getTemplateCode)
                .contains("TPL-BASE-STD", "TPL-FRAGILE", "TPL-EXPRESS", "TPL-COMMISSION_DELIVERER");
        PolicyTemplate base = offered.stream().filter(t -> t.getTemplateCode().equals("TPL-BASE-STD")).findFirst().orElseThrow();
        assertThat(base.getParameters()).extracting(p -> p.getKey())
                .containsExactlyInAnyOrder("basePrice", "perKmRate", "perKgRate", "minPrice", "maxDistance");

        Long paramsBefore = totalParameters();
        seeder.syncCatalog().block(Duration.ofSeconds(60));
        assertThat(repository.countActive().block()).isEqualTo(15L);
        assertThat(totalParameters()).as("no duplicated parameters").isEqualTo(paramsBefore);
    }

    @Test
    @DisplayName("a catalog left half-seeded (missing template, template without parameters) is repaired on the next start")
    void interruptedSeedingIsRepaired() throws Exception {
        TemplateCatalogSeeder seeder = new TemplateCatalogSeeder(repository);
        seeder.syncCatalog().block(Duration.ofSeconds(60));

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var st = conn.createStatement()) {
            // a restart between "template written" and "parameters written": the template exists, its parameters do not
            st.executeUpdate("DELETE FROM billing_template_parameters WHERE template_id = "
                    + "(SELECT id FROM billing_policy_templates WHERE template_code = 'TPL-FRAGILE')");
            // and a template that was never written
            st.executeUpdate("DELETE FROM billing_template_parameters WHERE template_id = "
                    + "(SELECT id FROM billing_policy_templates WHERE template_code = 'TPL-EXPRESS')");
            st.executeUpdate("DELETE FROM billing_policy_templates WHERE template_code = 'TPL-EXPRESS'");
        }
        assertThat(forFreelancer()).extracting(PolicyTemplate::getTemplateCode).doesNotContain("TPL-EXPRESS");

        seeder.syncCatalog().block(Duration.ofSeconds(60));

        List<PolicyTemplate> offered = forFreelancer();
        assertThat(offered).extracting(PolicyTemplate::getTemplateCode).contains("TPL-EXPRESS", "TPL-FRAGILE");
        assertThat(offered.stream().filter(t -> t.getTemplateCode().equals("TPL-FRAGILE")).findFirst().orElseThrow()
                .getParameters()).as("rebuilt parameters").hasSizeGreaterThanOrEqualTo(3);
        assertThat(repository.countActive().block()).isEqualTo(15L);
    }

    @Test
    @DisplayName("an admin-edited but complete template is left alone")
    void completeTemplatesAreNotOverwritten() throws Exception {
        TemplateCatalogSeeder seeder = new TemplateCatalogSeeder(repository);
        seeder.syncCatalog().block(Duration.ofSeconds(60));
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var st = conn.createStatement()) {
            st.executeUpdate("UPDATE billing_policy_templates SET name = 'Mon tarif maison' WHERE template_code = 'TPL-BASE-STD'");
        }
        seeder.syncCatalog().block(Duration.ofSeconds(60));
        assertThat(repository.findByTemplateCode("TPL-BASE-STD").block().getName()).isEqualTo("Mon tarif maison");
    }

    private static Long totalParameters() {
        return repository.findAll().flatMapIterable(PolicyTemplate::getParameters).count().block();
    }
}
