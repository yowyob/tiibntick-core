package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubCustodyPort;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRevocationListener;
import com.yowyob.tiibntick.core.geo.application.service.RelayHubApprovalService;
import com.yowyob.tiibntick.core.geo.application.service.RelayHubRevocationService;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
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
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-129, D-135, D-138 to D-140 on a real PostGIS database migrated by the geo-core changelog (gdd-l4-418, gdd-l4-421):
 * approval machine and revocation persisted, only approved hubs proposed and taking deposits.
 */
@Testcontainers
@Tag("integration")
@DisplayName("Relay hub revocation (geo-core) on PostGIS")
class R2dbcRelayHubRevocationIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tnt_geo_hub_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    static R2dbcRelayHubRepository repository;
    static final UUID TENANT = UUID.randomUUID();
    static final UUID ADMIN = UUID.randomUUID();

    @BeforeAll
    static void migrateAndWire() throws Exception {
        try (Connection conn = jdbc()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-geo-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
        }
        ConnectionFactory cf = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":"
                + POSTGRES.getPassword() + "@" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        repository = new R2dbcRelayHubRepository(new R2dbcEntityTemplate(cf), DatabaseClient.create(cf));
    }

    private static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static RelayHub newHub() throws Exception {
        return newHub(3.8, 11.5);
    }

    private static RelayHub newHub(double lat, double lon) throws Exception {
        RoadNodeId node = RoadNodeId.generate();
        try (Connection c = jdbc(); var s = c.createStatement()) {
            s.execute("INSERT INTO tnt_geography.road_nodes (id, tenant_id, type, latitude, longitude, name, city_code) "
                    + "VALUES ('" + node.value() + "', '" + TENANT + "', 'RELAY_HUB', " + lat + ", " + lon
                    + ", 'Relais', 'YDE')");
        }
        return repository.save(RelayHub.create(TENANT, null, node, 4, "op")).block();
    }

    private static RelayHub approvedHub() throws Exception {
        RelayHub hub = newHub();
        return approvals().approve(TENANT, hub.id(), ADMIN).block();
    }

    private static RelayHubApprovalService approvals() {
        return new RelayHubApprovalService(repository,
                new StaticListableBeanFactory().getBeanProvider(
                        com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubApprovalListener.class));
    }

    @Test
    @DisplayName("the real service revokes an approved hub, persists reason/date/administrator, lists the custody and "
            + "tells listeners; a second revocation and a pending hub's revocation are refused")
    void revokeThroughTheRealService() throws Exception {
        RelayHub hub = approvedHub();
        HubCustodyItem item = new HubCustodyItem(UUID.randomUUID(), UUID.randomUUID(), "TRK1", UUID.randomUUID(), Instant.now());
        List<UUID> told = new ArrayList<>();
        StaticListableBeanFactory custody = new StaticListableBeanFactory();
        custody.addBean("custody", (IRelayHubCustodyPort) id -> id.equals(hub.id()) ? Flux.just(item) : Flux.empty());
        StaticListableBeanFactory listeners = new StaticListableBeanFactory();
        listeners.addBean("l", (IRelayHubRevocationListener) r -> Mono.fromRunnable(() -> told.add(r.hub().id())));
        RelayHubRevocationService service = new RelayHubRevocationService(repository,
                custody.getBeanProvider(IRelayHubCustodyPort.class), listeners.getBeanProvider(IRelayHubRevocationListener.class));

        StepVerifier.create(service.revoke(TENANT, hub.id(), "Colis perdus à répétition", ADMIN))
                .assertNext(r -> assertThat(r.parcelsInCustody()).containsExactly(item))
                .verifyComplete();
        assertThat(told).containsExactly(hub.id());

        RelayHub read = repository.findById(hub.id(), TENANT).block();
        assertThat(read.approvalStatus()).isEqualTo(HubApprovalStatus.REVOKED);
        assertThat(read.status()).isEqualTo(HubStatus.ACTIVE);
        assertThat(read.revocationReason()).isEqualTo("Colis perdus à répétition");
        assertThat(read.revokedByActorId()).isEqualTo(ADMIN);
        assertThat(read.revokedAt()).isNotNull();

        StepVerifier.create(service.revoke(TENANT, hub.id(), "again", ADMIN)).expectError(RelayHubStateException.class).verify();
        assertThat(repository.findById(hub.id(), TENANT).block().revocationReason()).isEqualTo("Colis perdus à répétition");

        RelayHub pending = newHub();
        StepVerifier.create(service.revoke(TENANT, pending.id(), "r", ADMIN)).expectError(RelayHubStateException.class).verify();
        assertThat(repository.findById(pending.id(), TENANT).block().approvalStatus()).isEqualTo(HubApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("D-138/D-139: approve, suspend, reactivate persisted; repeated request is a no-op; refused transition 409")
    void approvalMachinePersisted() throws Exception {
        RelayHub hub = newHub();
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.PENDING);
        RelayHubApprovalService service = approvals();

        StepVerifier.create(service.reactivate(TENANT, hub.id(), ADMIN)).expectError(RelayHubStateException.class).verify();
        service.approve(TENANT, hub.id(), ADMIN).block();
        service.suspend(TENANT, hub.id(), "contrôle", ADMIN).block();
        Instant suspendedAt = repository.findById(hub.id(), TENANT).block().updatedAt();
        assertThat(service.suspend(TENANT, hub.id(), null, ADMIN).block().updatedAt())
                .as("suspending again changes nothing").isEqualTo(suspendedAt);
        assertThat(repository.findById(hub.id(), TENANT).block().approvalStatus()).isEqualTo(HubApprovalStatus.SUSPENDED);
        StepVerifier.create(service.approve(TENANT, hub.id(), ADMIN)).expectError(RelayHubStateException.class).verify();
        service.reactivate(TENANT, hub.id(), ADMIN).block();
        assertThat(repository.findById(hub.id(), TENANT).block().approvalStatus()).isEqualTo(HubApprovalStatus.APPROVED);

        RelayHub other = newHub();
        service.reject(TENANT, other.id(), "dossier incomplet", ADMIN).block();
        assertThat(repository.findById(other.id(), TENANT).block().approvalStatus()).isEqualTo(HubApprovalStatus.REJECTED);
        StepVerifier.create(service.approve(TENANT, other.id(), ADMIN)).expectError(RelayHubStateException.class).verify();
    }

    @Test
    @DisplayName("D-140: only approved open hubs are proposed (radius search, active list) and take deposits")
    void onlyApprovedHubsAreProposed() throws Exception {
        RelayHub approved = newHub(4.05, 9.7);
        approvals().approve(TENANT, approved.id(), ADMIN).block();
        RelayHub pending = newHub(4.0501, 9.7001);
        RelayHub suspended = newHub(4.0502, 9.7002);
        approvals().approve(TENANT, suspended.id(), ADMIN).block();
        approvals().suspend(TENANT, suspended.id(), null, ADMIN).block();

        List<UUID> nearby = repository.findAvailableWithinRadius(TENANT, GeoPoint.of(4.05, 9.7), 5.0)
                .map(RelayHub::id).collectList().block();
        assertThat(nearby).contains(approved.id()).doesNotContain(pending.id(), suspended.id());
        List<UUID> active = repository.findAllActive(TENANT).map(RelayHub::id).collectList().block();
        assertThat(active).contains(approved.id()).doesNotContain(pending.id(), suspended.id());

        StepVerifier.create(approvals().requireAcceptsDeposits(approved.id())).verifyComplete();
        StepVerifier.create(approvals().requireAcceptsDeposits(pending.id())).expectError(RelayHubStateException.class).verify();
        StepVerifier.create(approvals().requireAcceptsDeposits(suspended.id())).expectError(RelayHubStateException.class).verify();
        StepVerifier.create(approvals().requireAcceptsDeposits(UUID.randomUUID()))
                .as("a hub unknown to geo-core is not governed here").verifyComplete();
    }

    @Test
    @DisplayName("the occupancy update counts parcels leaving a revoked hub and never reopens a closed hub")
    void occupancyUpdateKeepsApprovalAndClosure() throws Exception {
        RelayHub hub = approvedHub();
        hub.permanentlyClose();
        hub.revoke("r", ADMIN);
        repository.save(hub).block();
        StepVerifier.create(repository.updateOccupancy(hub.id(), TENANT, 0)).expectNextCount(1).verifyComplete();
        RelayHub read = repository.findById(hub.id(), TENANT).block();
        assertThat(read.approvalStatus()).isEqualTo(HubApprovalStatus.REVOKED);
        assertThat(read.status()).isEqualTo(HubStatus.PERMANENTLY_CLOSED);
        assertThat(read.isAvailable()).isFalse();
        assertThat(repository.findAllActive(TENANT).filter(h -> h.id().equals(hub.id())).count().block()).isZero();
    }

    @Test
    @DisplayName("the database refuses a revoked hub without date, an unknown approval and REVOKED as operating status")
    void constraints() throws Exception {
        RelayHub hub = newHub();
        try (Connection c = jdbc(); var s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("UPDATE tnt_geography.relay_hubs SET approval_status = 'REVOKED' WHERE id = '"
                    + hub.id() + "'")).isInstanceOf(SQLException.class).hasMessageContaining("relay_hubs_approval_revocation_dated");
            assertThatThrownBy(() -> s.execute("UPDATE tnt_geography.relay_hubs SET approval_status = 'BANNED' WHERE id = '"
                    + hub.id() + "'")).isInstanceOf(SQLException.class).hasMessageContaining("relay_hubs_approval_status_check");
            assertThatThrownBy(() -> s.execute("UPDATE tnt_geography.relay_hubs SET status = 'REVOKED' WHERE id = '"
                    + hub.id() + "'")).isInstanceOf(SQLException.class).hasMessageContaining("relay_hubs_status_check");
            assertThatThrownBy(() -> s.execute("UPDATE tnt_geography.relay_hubs SET approval_status = NULL WHERE id = '"
                    + hub.id() + "'")).isInstanceOf(SQLException.class);
            s.execute("UPDATE tnt_geography.relay_hubs SET approval_status = 'REVOKED', revoked_at = now() "
                    + "WHERE id = '" + hub.id() + "'");
        }
        assertThat(repository.findById(hub.id(), TENANT).block().approvalStatus()).isEqualTo(HubApprovalStatus.REVOKED);
    }
}
