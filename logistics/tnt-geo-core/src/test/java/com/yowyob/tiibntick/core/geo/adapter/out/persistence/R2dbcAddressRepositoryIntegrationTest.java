package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.application.port.in.IManageAddressUseCase.AddressDetails;
import com.yowyob.tiibntick.core.geo.application.service.AddressService;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.Address;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/** The generic address book on a real PostGIS database migrated by the geo-core changelog. */
@Testcontainers
@Tag("integration")
@DisplayName("Address book (geo-core) on PostGIS")
class R2dbcAddressRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tnt_geo_address_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    static AddressService service;
    static final UUID TENANT = UUID.randomUUID();
    static final UUID OTHER_TENANT = UUID.randomUUID();

    @BeforeAll
    static void migrateAndWire() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-geo-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
        }
        ConnectionFactory cf = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":"
                + POSTGRES.getPassword() + "@" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        service = new AddressService(new R2dbcAddressRepository(DatabaseClient.create(cf)));
    }

    private static AddressDetails details(UUID id, String street, String landmark, String city) {
        return new AddressDetails(id, street, landmark, "Bastos", city, "Centre", "CM", "BP 1", 3.88, 11.51);
    }

    @Test
    @DisplayName("create keeps a given id, read-back is identical, update replaces the content and keeps the id")
    void createReadUpdate() {
        UUID id = UUID.randomUUID();
        StepVerifier.create(service.create(TENANT, details(id, "Rue 1.234", "Face pharmacie", "Yaounde"))
                        .then(service.findById(TENANT, id)))
                .assertNext(a -> {
                    assertThat(a.id()).isEqualTo(id);
                    assertThat(a.street()).isEqualTo("Rue 1.234");
                    assertThat(a.landmark()).isEqualTo("Face pharmacie");
                    assertThat(a.district()).isEqualTo("Bastos");
                    assertThat(a.latitude()).isEqualTo(3.88);
                    assertThat(a.tenantId()).isEqualTo(TENANT);
                }).verifyComplete();

        StepVerifier.create(service.update(TENANT, id, details(null, "Rue 2", null, "Douala"))
                        .then(service.findById(TENANT, id)))
                .assertNext(a -> {
                    assertThat(a.id()).isEqualTo(id);
                    assertThat(a.city()).isEqualTo("Douala");
                    assertThat(a.landmark()).isNull();
                }).verifyComplete();
    }

    @Test
    @DisplayName("an address of another tenant is not found; a tenant-less (migrated) address is visible to all tenants")
    void tenantVisibility() {
        UUID mine = UUID.randomUUID();
        UUID migrated = UUID.randomUUID();
        StepVerifier.create(service.create(TENANT, details(mine, "Rue privee", null, "Yaounde")).then(
                service.create(null, details(migrated, "Rue ancienne", null, "Yaounde")).then()))
                .verifyComplete();

        StepVerifier.create(service.findById(OTHER_TENANT, mine)).expectError(GeoNotFoundException.class).verify();
        StepVerifier.create(service.findById(OTHER_TENANT, migrated)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.findById(TENANT, migrated)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("text search is case-insensitive over street and landmark, and % _ are literal")
    void search() {
        UUID byLandmark = UUID.randomUUID();
        UUID byStreet = UUID.randomUUID();
        StepVerifier.create(service.create(TENANT, details(byLandmark, null, "Carrefour ZZmarche", "Yaounde"))
                .then(service.create(TENANT, details(byStreet, "Avenue zzmarche 50%", null, "Yaounde"))).then())
                .verifyComplete();

        StepVerifier.create(service.search(TENANT, "ZZMARCHE").map(Address::id).collectList())
                .assertNext(ids -> assertThat(ids).containsExactlyInAnyOrder(byLandmark, byStreet))
                .verifyComplete();
        StepVerifier.create(service.search(TENANT, "50%").map(Address::id).collectList())
                .assertNext(ids -> assertThat(ids).containsExactly(byStreet)).verifyComplete();
        StepVerifier.create(service.search(TENANT, "z_marche")).expectNextCount(0).verifyComplete();
        StepVerifier.create(service.search(TENANT, "  ")).expectNextCount(0).verifyComplete();
        StepVerifier.create(service.search(OTHER_TENANT, "zzmarche")).expectNextCount(0).verifyComplete();
    }

    @Test
    @DisplayName("links: idempotent per (owner, address, label), several labels allowed, removed with the address")
    void links() {
        UUID owner = UUID.randomUUID();
        UUID addr = UUID.randomUUID();
        StepVerifier.create(service.create(TENANT, details(addr, "Rue lien", null, "Yaounde")).then(
                        service.link(owner, addr, "HOME").then(service.link(owner, addr, "HOME"))
                                .then(service.link(owner, addr, "WORK"))).then())
                .verifyComplete();

        StepVerifier.create(service.findLinksByOwner(owner).map(l -> l.label()).collectList())
                .assertNext(labels -> assertThat(labels).containsExactlyInAnyOrder("HOME", "WORK"))
                .verifyComplete();

        StepVerifier.create(service.findLinksByAddress(addr).map(l -> l.ownerId()).distinct())
                .expectNext(owner).verifyComplete();

        StepVerifier.create(service.delete(TENANT, addr).thenMany(service.findLinksByOwner(owner)))
                .expectNextCount(0).verifyComplete();
        StepVerifier.create(service.findById(TENANT, addr)).expectError(GeoNotFoundException.class).verify();
    }

    @Test
    @DisplayName("invalid content is refused: a city, a country and a street or a landmark are required")
    void validation() {
        StepVerifier.create(service.create(TENANT, details(null, null, null, "Yaounde")))
                .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.create(TENANT, details(null, "Rue", null, " ")))
                .expectError(IllegalArgumentException.class).verify();
    }
}
