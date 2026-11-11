package com.yowyob.kernel.i18n.integration;

import com.yowyob.kernel.i18n.adapter.r2dbc.R2dbcMessageCatalogAdapter;
import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.application.service.MessageCatalogAdminService;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The real changelog (schema + seed) and the R2DBC adapter on PostgreSQL. */
@Testcontainers
@Tag("integration")
class MessageCatalogIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("i18n_it").withUsername("tnt_test").withPassword("tnt_test_secret");

    static R2dbcMessageCatalogAdapter port;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/yow-i18n-kernel-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
            // idempotent re-run: the seed must not duplicate or fail
            new Liquibase("db/changelog/yow-i18n-kernel-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
        }
        ConnectionFactory cf = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":"
                + POSTGRES.getPassword() + "@" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        port = new R2dbcMessageCatalogAdapter(DatabaseClient.create(cf));
    }

    private static MessageCatalog loadedCatalog() {
        MessageCatalog catalog = new MessageCatalog(port, null);
        catalog.reload().block(Duration.ofSeconds(10));
        return catalog;
    }

    @Test
    void seedCarriesTheExistingMessagesInFrenchAndEnglish() {
        MessageCatalog catalog = loadedCatalog();
        assertThat(catalog.size()).isGreaterThanOrEqualTo(122);
        Map<String, Object> p = Map.of("package_id", "PKG-9", "driver_name", "Jo");
        assertThat(catalog.render(null, "notification.package.in_transit", "fr", null, p).orElseThrow())
                .contains("PKG-9").contains("Jo").doesNotContain("{{");
        assertThat(catalog.render(null, "notification.package.in_transit", "en", null, p).orElseThrow())
                .contains("PKG-9").contains("Jo").doesNotContain("{{");
        assertThat(catalog.render(null, "notification.package.in_transit", "fr", null, p).orElseThrow())
                .isNotEqualTo(catalog.render(null, "notification.package.in_transit", "en", null, p).orElseThrow());
        assertThat(catalog.namespaces()).contains("notify", "errors", "ref");
        for (String ns : List.of("notify", "errors", "ref")) {
            StepVerifier.create(port.findMissing(ns, List.of("fr", "en"))).verifyComplete();
        }
    }

    @Test
    void publishingWritesAVersionAndTheCacheServesItAtOnce() {
        MessageCatalog catalog = loadedCatalog();
        MessageCatalogAdminService admin = new MessageCatalogAdminService(port, catalog);

        MessageEntry v1 = admin.save("t_publish", "greeting", "fr", null, "Bonjour {{name}}", true, "admin-1").block();
        assertThat(v1.version()).isEqualTo(1);
        MessageEntry v2 = admin.save("t_publish", "greeting", "fr", "DEFAULT", "Salut {{name}}", true, "admin-2").block();
        assertThat(v2.version()).isEqualTo(2);

        assertThat(catalog.render("t_publish", "greeting", "fr", null, Map.of("name", "Jo"))).contains("Salut Jo");
        StepVerifier.create(admin.history("t_publish", "greeting", "fr", null))
                .assertNext(h -> assertThat(h.text()).isEqualTo("Salut {{name}}"))
                .assertNext(h -> assertThat(h.text()).isEqualTo("Bonjour {{name}}"))
                .verifyComplete();
        // The English side is missing until it is written.
        StepVerifier.create(admin.missing("t_publish"))
                .assertNext(m -> {
                    assertThat(m.key()).isEqualTo("greeting");
                    assertThat(m.missingLanguage()).isEqualTo("en");
                })
                .verifyComplete();
    }

    @Test
    void aDraftIsInvisibleUntilPublished() {
        MessageCatalog catalog = loadedCatalog();
        MessageCatalogAdminService admin = new MessageCatalogAdminService(port, catalog);
        admin.save("t_draft", "draft.me", "en", null, "Live text", true, "a").block();
        MessageEntry draft = admin.save("t_draft", "draft.me", "en", null, "Edited text", false, "a").block();
        assertThat(draft.text()).isEqualTo("Live text");
        assertThat(draft.draftText()).isEqualTo("Edited text");
        assertThat(catalog.resolve("t_draft", "draft.me", "en", null)).contains("Live text");
        StepVerifier.create(admin.search("t_draft", "draft.", "en", true, 10, 0)).expectNextCount(1).verifyComplete();

        MessageEntry published = admin.publishDraft("t_draft", "draft.me", "en", null, "b").block();
        assertThat(published.text()).isEqualTo("Edited text");
        assertThat(published.draftText()).isNull();
        assertThat(published.version()).isEqualTo(2);
        assertThat(catalog.resolve("t_draft", "draft.me", "en", null)).contains("Edited text");
        // nothing left to publish
        assertThat(admin.publishDraft("t_draft", "draft.me", "en", null, "b").block()).isNull();
    }

    @Test
    void aBrandNewKeyCanStartAsADraft() {
        MessageCatalog catalog = loadedCatalog();
        MessageCatalogAdminService admin = new MessageCatalogAdminService(port, catalog);
        MessageEntry draft = admin.save("t_newdraft", "only.draft", "fr", null, "Brouillon", false, "a").block();
        assertThat(draft.isLive()).isFalse();
        assertThat(draft.hasDraft()).isTrue();
        catalog.reload().block();
        assertThat(catalog.resolve("t_newdraft", "only.draft", "fr", null)).isEmpty();
    }

    @Test
    void revisionMovesWhenTheCatalogChangesSoOtherInstancesReload() {
        MessageCatalog catalog = loadedCatalog();
        port.save("t_rev", "rev.test", "fr", "DEFAULT", "x", true, "sql").block();
        assertThat(catalog.refreshIfChanged().block()).isTrue();
        assertThat(catalog.resolve("t_rev", "rev.test", "fr", null)).contains("x");
        assertThat(catalog.refreshIfChanged().block()).isFalse();
    }

    @Test
    void invalidInputIsRejectedBeforeTouchingTheDatabase() {
        MessageCatalogAdminService admin = new MessageCatalogAdminService(port, loadedCatalog());
        assertThatThrownBy(() -> admin.save("Bad NS", "k", "fr", null, "x", true, "a").block())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.save("ui", "k", "de", null, "x", true, "a").block())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.save("ui", "k", "fr", null, "broken {{x", true, "a").block())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.save("ui", "k", "fr", "FAX", "x", true, "a").block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notificationScenariosAreSeededWithChannelFlavours() {
        MessageCatalog catalog = loadedCatalog();
        Map<String, Object> facts = Map.of("reference", "INC-9", "level", 2, "severity", "HIGH");
        assertThat(catalog.render("notify", "incident.notification.incident.escalated", "fr", null, facts).orElseThrow())
                .isEqualTo("Incident INC-9 escaladé (niveau 2) — gravité : HIGH.");
        assertThat(catalog.render("notify", "incident.notification.incident.escalated", "en", null, facts).orElseThrow())
                .isEqualTo("Incident INC-9 escalated (level 2) — severity: HIGH.");
        // push flavour is shorter than the plain text; a channel without a flavour reads the plain text
        assertThat(catalog.render("notify", "incident.notification.incident.escalated", "en", "PUSH", facts).orElseThrow())
                .isEqualTo("Incident INC-9 escalated (lvl 2)");
        assertThat(catalog.render("notify", "incident.notification.incident.escalated", "en", "SMS", facts).orElseThrow())
                .startsWith("Incident INC-9 escalated (level 2)");
        assertThat(catalog.render("notify", "account.welcome", "en", "EMAIL_SUBJECT", Map.of()).orElseThrow())
                .isEqualTo("Welcome to TiiBnTick");
        assertThat(catalog.resolve("notify", "notify.sub_deliverer.invited", "fr", null)).isPresent();
        assertThat(catalog.resolve("notify", "notify.billing.surcharge_triggered", "en", null)).isPresent();
    }

    @Test
    void reapplyingTheSeedKeepsAnAdminEdit() throws Exception {
        MessageCatalog catalog = loadedCatalog();
        MessageCatalogAdminService admin = new MessageCatalogAdminService(port, catalog);
        admin.save("notify", "incident.notification.incident.created", "en", "DEFAULT",
                "Edited by an admin for {{reference}}", true, "admin-it").block(Duration.ofSeconds(10));
        String sql;
        try (var in = MessageCatalogIntegrationTest.class.getClassLoader().getResourceAsStream(
                "db/changelog/changes/i18n-003-seed-notification-scenarios.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
        MessageCatalog reloaded = loadedCatalog();
        assertThat(reloaded.render("notify", "incident.notification.incident.created", "en", null,
                Map.of("reference", "X")).orElseThrow()).isEqualTo("Edited by an admin for X");
        // untouched seed rows are left identical
        assertThat(reloaded.render("notify", "incident.notification.incident.created", "fr", null,
                Map.of("reference", "X", "incidentType", "T", "missionId", "M")).orElseThrow())
                .isEqualTo("Nouvel incident X — type T, mission M.");
    }
}
