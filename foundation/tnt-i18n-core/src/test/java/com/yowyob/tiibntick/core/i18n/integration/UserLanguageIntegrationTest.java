package com.yowyob.tiibntick.core.i18n.integration;

import com.yowyob.tiibntick.core.i18n.adapter.out.persistence.R2dbcUserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.application.service.UserLanguageService;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The real changeset and the R2DBC repository on PostgreSQL. */
@Testcontainers
@Tag("integration")
class UserLanguageIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("i18n_core_it").withUsername("tnt_test").withPassword("tnt_test_secret");

    static R2dbcUserLanguagePreferenceRepository repository;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            for (int i = 0; i < 2; i++) { // second run proves the changeset is idempotent
                new Liquibase("db/changelog/tnt-i18n-master.yaml", new ClassLoaderResourceAccessor(), database)
                        .update(new Contexts(), new LabelExpression());
            }
        }
        ConnectionFactory cf = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":"
                + POSTGRES.getPassword() + "@" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        repository = new R2dbcUserLanguagePreferenceRepository(DatabaseClient.create(cf));
    }

    @Test
    void anAccountWithoutPreferenceHasNone() {
        StepVerifier.create(repository.findByUserId(UUID.randomUUID())).verifyComplete();
    }

    @Test
    void upsertCreatesThenReplaces() {
        UUID u = UUID.randomUUID();
        StepVerifier.create(repository.upsert(u, "fr", "USER")).assertNext(p -> {
            assertThat(p.language()).isEqualTo("fr");
            assertThat(p.updatedAt()).isNotNull();
        }).verifyComplete();
        StepVerifier.create(repository.upsert(u, "en", "USER"))
                .assertNext(p -> assertThat(p.language()).isEqualTo("en")).verifyComplete();
        StepVerifier.create(repository.findByUserId(u))
                .assertNext(p -> assertThat(p.language()).isEqualTo("en")).verifyComplete();
    }

    @Test
    void detectionOnlyFillsAnEmptyAccountAndNeverOverridesAChoice() {
        UserLanguageService service = new UserLanguageService(repository, Duration.ofMillis(1));
        UUID fresh = UUID.randomUUID();
        StepVerifier.create(service.detect(fresh, "en")).assertNext(p -> {
            assertThat(p.language()).isEqualTo("en");
            assertThat(p.source()).isEqualTo("DETECTED");
        }).verifyComplete();
        // an explicit choice replaces a detection...
        StepVerifier.create(service.choose(fresh, "fr")).assertNext(p -> assertThat(p.source()).isEqualTo("USER"))
                .verifyComplete();
        // ...and a later detection leaves the choice alone
        StepVerifier.create(service.detect(fresh, "en")).assertNext(p -> {
            assertThat(p.language()).isEqualTo("fr");
            assertThat(p.source()).isEqualTo("USER");
        }).verifyComplete();
    }

    @Test
    void theRecipientLanguageIsReadFromTheDatabase() {
        UserLanguageService service = new UserLanguageService(repository, Duration.ofMillis(1));
        UUID client = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        service.choose(client, "en").block();
        service.choose(driver, "fr").block();
        // same event, two recipients, two languages
        StepVerifier.create(service.languageOf(client.toString(), "fr")).expectNext("en").verifyComplete();
        StepVerifier.create(service.languageOf(driver.toString(), "en")).expectNext("fr").verifyComplete();
    }
}
