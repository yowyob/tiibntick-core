package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision D-138: the relay hubs get an approval status (gdd-l4-421, geo-core) whose initial value is deduced without
 * invention — the status of the Go relay point backing the hub (gdd-l4-422), else APPROVED for the hubs that operated
 * without approval, REVOKED for the hubs revoked under gdd-l4-418. Migrated from the previous state
 * ({@code !gdd-l4-approval}) on PostGIS, then replayed.
 */
@Testcontainers
@Tag("integration")
class Gdd4HubApprovalMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    private static final String APPROVAL_LABEL = "gdd-l4-approval";
    private static final String GEO_SQL = "../logistics/tnt-geo-core/src/main/resources/db/changelog/gdd-l4/"
            + "V421__gdd_l4_421_relay_hub_approval_status.sql";
    private static final String GO_SQL = "../coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/"
            + "20261126_gdd_l4_422_relay_hub_approval_from_relay_points.sql";

    private final UUID tenant = UUID.randomUUID();

    @Test
    @DisplayName("initial approval: Go relay point status copied, other hubs approved, revoked hubs kept revoked")
    void initialApprovalIsDeduced() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            liquibase(conn).update(new Contexts(), new LabelExpression("!" + APPROVAL_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'tnt_geography'"
                    + " AND table_name = 'relay_hubs' AND column_name = 'approval_status'")).as("previous state").isZero();

            UUID goPending = hub(conn, null, "ACTIVE");
            UUID goApproved = hub(conn, null, "FULL");
            UUID goSuspended = hub(conn, null, "ACTIVE");
            UUID goRejected = hub(conn, null, "ACTIVE");
            UUID goRevoked = hub(conn, null, "ACTIVE");
            UUID goUnknownStatus = hub(conn, null, "ACTIVE");
            UUID agency = hub(conn, UUID.randomUUID(), "ACTIVE");
            UUID closed = hub(conn, UUID.randomUUID(), "TEMPORARILY_CLOSED");
            UUID revoked418 = hub(conn, null, "ACTIVE");
            exec(conn, "UPDATE tnt_geography.relay_hubs SET status = 'REVOKED', revocation_reason = 'Fraude', "
                    + "revoked_at = '2026-11-20T10:00:00Z' WHERE id = '" + revoked418 + "'");
            relayPoint(conn, goPending, "PENDING");
            relayPoint(conn, goApproved, "APPROVED");
            relayPoint(conn, goSuspended, "SUSPENDED");
            relayPoint(conn, goRejected, "REJECTED");
            relayPoint(conn, goRevoked, "REVOKED");
            relayPoint(conn, goUnknownStatus, "ACTIVE");
            relayPoint(conn, UUID.randomUUID(), "APPROVED"); // its hub was never provisioned in geo-core
            long hubs = count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs");

            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            assertApproval(conn, goPending, "PENDING", "ACTIVE");
            assertApproval(conn, goApproved, "APPROVED", "FULL");
            assertApproval(conn, goSuspended, "SUSPENDED", "ACTIVE");
            assertApproval(conn, goRejected, "REJECTED", "ACTIVE");
            assertApproval(conn, goRevoked, "REVOKED", "ACTIVE");
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE id = '" + goRevoked
                    + "' AND revoked_at = '2026-10-01T00:00:00Z' AND revocation_reason IS NULL"))
                    .as("revoked relay point: dated by its last update, no invented reason").isEqualTo(1);
            assertApproval(conn, goUnknownStatus, "APPROVED", "ACTIVE");
            assertApproval(conn, agency, "APPROVED", "ACTIVE");
            assertApproval(conn, closed, "APPROVED", "TEMPORARILY_CLOSED");
            assertApproval(conn, revoked418, "REVOKED", "PERMANENTLY_CLOSED");
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE id = '" + revoked418
                    + "' AND revocation_reason = 'Fraude' AND revoked_at = '2026-11-20T10:00:00Z'")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs")).as("no hub created or deleted")
                    .isEqualTo(hubs);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE approval_status IS NULL")).isZero();

            exec(conn, "INSERT INTO tnt_geography.road_nodes (id, tenant_id, type, latitude, longitude, name, city_code) "
                    + "VALUES ('n-new', '" + tenant + "', 'RELAY_HUB', 3.8, 11.5, 'Relais', 'YDE')");
            exec(conn, "INSERT INTO tnt_geography.relay_hubs (id, tenant_id, node_id, capacity_slots, current_occupancy, status) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + tenant + "', 'n-new', 4, 0, 'ACTIVE')");
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE node_id = 'n-new' "
                    + "AND approval_status = 'PENDING'")).as("a new hub waits for its approval").isEqualTo(1);

            String before = snapshot(conn);
            exec(conn, Files.readString(Path.of(GEO_SQL)));
            exec(conn, Files.readString(Path.of(GO_SQL)));
            assertThat(snapshot(conn)).as("replay is idempotent").isEqualTo(before);
        }
    }

    private UUID hub(Connection conn, UUID branch, String status) throws Exception {
        UUID id = UUID.randomUUID();
        String node = "n-" + id.toString().substring(0, 30);
        exec(conn, "INSERT INTO tnt_geography.road_nodes (id, tenant_id, type, latitude, longitude, name, city_code) "
                + "VALUES ('" + node + "', '" + tenant + "', 'RELAY_HUB', 3.8, 11.5, 'Relais', 'YDE')");
        exec(conn, "INSERT INTO tnt_geography.relay_hubs (id, tenant_id, branch_id, node_id, capacity_slots, "
                + "current_occupancy, status) VALUES ('" + id + "', '" + tenant + "', " + (branch == null ? "NULL" : "'" + branch + "'")
                + ", '" + node + "', 4, " + ("FULL".equals(status) ? 4 : 0) + ", '" + status + "')");
        return id;
    }

    private static void relayPoint(Connection conn, UUID hubId, String status) throws Exception {
        insert(conn, "gofp_relay_points", Map.of("id", UUID.randomUUID(), "core_relay_point_id", hubId,
                "status", status, "updated_at", "2026-10-01T00:00:00Z"));
    }

    private static void assertApproval(Connection conn, UUID hub, String approval, String status) throws Exception {
        assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE id = '" + hub
                + "' AND approval_status = '" + approval + "' AND status = '" + status + "'"))
                .as("hub expected %s / %s", approval, status).isEqualTo(1);
    }

    private static String snapshot(Connection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT id, approval_status, status, revoked_at, revocation_reason FROM tnt_geography.relay_hubs ORDER BY id")) {
            while (rs.next()) {
                for (int i = 1; i <= 5; i++) sb.append(rs.getString(i)).append('|');
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
