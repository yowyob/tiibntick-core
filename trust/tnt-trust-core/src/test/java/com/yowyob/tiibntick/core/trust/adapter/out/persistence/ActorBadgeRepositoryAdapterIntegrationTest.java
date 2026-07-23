package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ActorBadge;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Postgres integration test for {@link ActorBadgeRepositoryAdapter}.
 *
 * <p>Unlike the module's existing Mockito-based {@code PersistenceAdapterTest}, this
 * test proves the {@code tnt-trust} Liquibase changelog
 * ({@code db/changelog/tnt-trust-master.yaml}) end-to-end against a real PostgreSQL
 * container: the schema is created by running the actual changelog via Liquibase's
 * Java API (not a hand-copied {@code CREATE TABLE}), and a real Spring Data R2DBC
 * repository proxy (built via {@link R2dbcRepositoryFactory}, wrapped by the real
 * {@link ActorBadgeRepositoryAdapter}) is exercised against it.
 *
 * <p>Tagged {@code @Tag("integration")} so it is excluded from the default
 * {@code mvn test} (Surefire) run and only executes under
 * {@code mvn verify -Pintegration-tests} (Failsafe), per this repo's conventions.
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class ActorBadgeRepositoryAdapterIntegrationTest {

    // NOTE: postgis/postgis is not on Testcontainers' known-compatible-image allowlist
    // for PostgreSQLContainer (newer Testcontainers versions assert image name compatibility
    // at construction time) — declare it explicitly so the "postgres"-flavoured JDBC/wait-strategy
    // logic in PostgreSQLContainer still applies to this Postgres-compatible image.
    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("tnt_trust_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static ActorBadgeRepositoryAdapter adapter;
    private static DatabaseClient databaseClient;

    @BeforeAll
    static void migrateSchemaAndWireAdapter() throws Exception {
        // 1. Run the REAL Liquibase changelog against the container (JDBC, schema-only —
        //    mirrors this module's production bootstrap path, not a hand-copied DDL stub).
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            final Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            final Liquibase liquibase = new Liquibase(
                    "db/changelog/tnt-trust-master.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        }

        // 2. Wire a REAL Spring Data R2DBC repository proxy against the container,
        //    then wrap it with the real ActorBadgeRepositoryAdapter (no mocks).
        final PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();

        final ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        final R2dbcEntityTemplate template = new R2dbcEntityTemplate(connectionFactory);
        databaseClient = DatabaseClient.create(connectionFactory);

        final R2dbcRepositoryFactory repositoryFactory = new R2dbcRepositoryFactory(template);
        final ActorBadgeR2dbcRepository r2dbcRepository =
                repositoryFactory.getRepository(ActorBadgeR2dbcRepository.class);

        adapter = new ActorBadgeRepositoryAdapter(r2dbcRepository);
    }

    @AfterAll
    static void cleanup() {
        if (databaseClient != null) {
            databaseClient.sql("DELETE FROM tnt_trust.actor_badges").then().block();
        }
    }

    @BeforeEach
    void cleanTable() {
        databaseClient.sql("DELETE FROM tnt_trust.actor_badges").then().block();
    }

    /** actor_id is {@code VARCHAR(36)} — a bare UUID string is exactly 36 chars, so no prefix. */
    private static String uniqueActorId() {
        return UUID.randomUUID().toString();
    }

    private static final String TENANT_ID = "tenant-integration-test";

    @Test
    @DisplayName("save() then findByActorId() returns the persisted badge")
    void saveThenFindByActorId() {
        final String actorId = uniqueActorId();
        final ActorBadge badge = ActorBadge.award(actorId, TENANT_ID, "100_DELIVERIES", 100);

        StepVerifier.create(
                        adapter.save(badge)
                                .thenMany(adapter.findByActorId(actorId, TENANT_ID)))
                .assertNext(found -> {
                    assertThat(found.getBadgeId()).isEqualTo(badge.getBadgeId());
                    assertThat(found.getActorId()).isEqualTo(actorId);
                    assertThat(found.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(found.getBadgeType()).isEqualTo("100_DELIVERIES");
                    assertThat(found.getPoints()).isEqualTo(100);
                    assertThat(found.isRevoked()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findByActorIdAndBadgeType() finds the right badge among several")
    void findByActorIdAndBadgeType() {
        final String actorId = uniqueActorId();
        final ActorBadge deliveries = ActorBadge.award(actorId, TENANT_ID, "100_DELIVERIES", 100);
        final ActorBadge topRated = ActorBadge.award(actorId, TENANT_ID, "TOP_RATED", 50);

        StepVerifier.create(
                        adapter.save(deliveries)
                                .then(adapter.save(topRated))
                                .then(adapter.findByActorIdAndBadgeType(actorId, "TOP_RATED", TENANT_ID)))
                .assertNext(found -> {
                    assertThat(found.getBadgeId()).isEqualTo(topRated.getBadgeId());
                    assertThat(found.getBadgeType()).isEqualTo("TOP_RATED");
                    assertThat(found.getPoints()).isEqualTo(50);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("updateTxHash() persists the blockchain tx hash")
    void updateTxHashPersists() {
        final String actorId = uniqueActorId();
        final ActorBadge badge = ActorBadge.award(actorId, TENANT_ID, "ZERO_CLAIM", 20);
        final String txHash = "a".repeat(64);

        StepVerifier.create(
                        adapter.save(badge)
                                .then(adapter.updateTxHash(badge.getBadgeId(), txHash))
                                .then(adapter.findByActorIdAndBadgeType(actorId, "ZERO_CLAIM", TENANT_ID)))
                .assertNext(found -> assertThat(found.getBlockchainTxHash()).isEqualTo(txHash))
                .verifyComplete();
    }

    @Test
    @DisplayName("revokeByBadgeId() marks the badge revoked and excludes it from findByActorId()")
    void revokeByBadgeIdExcludesFromActiveList() {
        final String actorId = uniqueActorId();
        final ActorBadge badge = ActorBadge.award(actorId, TENANT_ID, "ZONE_VETERAN", 30);

        StepVerifier.create(
                        adapter.save(badge)
                                .then(adapter.revokeByBadgeId(badge.getBadgeId()))
                                .thenMany(adapter.findByActorId(actorId, TENANT_ID)))
                .verifyComplete(); // findActiveByActorId excludes revoked=TRUE rows -> empty Flux

        // Sanity-check that the row actually exists (revoked), via a raw query.
        final Boolean stillExists = databaseClient.sql(
                        "SELECT revoked FROM tnt_trust.actor_badges WHERE badge_id = :badgeId")
                .bind("badgeId", badge.getBadgeId())
                .map(row -> row.get("revoked", Boolean.class))
                .one()
                .block();
        assertThat(stillExists).isTrue();
    }

    @Test
    @DisplayName("existsByActorAndType() returns true when an active badge of that type exists")
    void existsByActorAndTypeTrueCase() {
        final String actorId = uniqueActorId();
        final ActorBadge badge = ActorBadge.award(actorId, TENANT_ID, "CERTIFIED_RELAY_OPERATOR", 40);

        StepVerifier.create(
                        adapter.save(badge)
                                .then(adapter.existsByActorAndType(actorId, "CERTIFIED_RELAY_OPERATOR", TENANT_ID)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("existsByActorAndType() returns false when no such badge exists")
    void existsByActorAndTypeFalseCase() {
        final String actorId = uniqueActorId();

        StepVerifier.create(adapter.existsByActorAndType(actorId, "TOP_RATED", TENANT_ID))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("existsByActorAndType() returns false once the only matching badge has been revoked")
    void existsByActorAndTypeFalseAfterRevoke() {
        final String actorId = uniqueActorId();
        final ActorBadge badge = ActorBadge.award(actorId, TENANT_ID, "TOP_RATED", 60);

        StepVerifier.create(
                        adapter.save(badge)
                                .then(adapter.revokeByBadgeId(badge.getBadgeId()))
                                .then(adapter.existsByActorAndType(actorId, "TOP_RATED", TENANT_ID)))
                .expectNext(false)
                .verifyComplete();
    }
}
