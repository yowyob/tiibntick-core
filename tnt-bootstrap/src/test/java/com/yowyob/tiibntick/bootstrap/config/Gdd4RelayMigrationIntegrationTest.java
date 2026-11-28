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

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4: relay deposits become inventory hub package entries (gdd-l4-412) and the occupancy of the relay hubs is
 * recomputed (gdd-l4-413). No deposit in progress is lost.
 */
@Testcontainers
@Tag("integration")
class Gdd4RelayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    private static final String CHANGELOG = "../coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/";

    @Test
    @DisplayName("deposits are merged into or copied to the inventory with owner and fees; hub occupancy is recomputed")
    void relayDepositsAreMigrated() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);

            UUID tenant = UUID.randomUUID();
            UUID hub = UUID.randomUUID();
            UUID unknownHub = UUID.randomUUID();
            exec(conn, "INSERT INTO tnt_geography.road_nodes (id, tenant_id, type, latitude, longitude, name, city_code) "
                    + "VALUES ('n-" + hub.toString().substring(0, 30) + "', '" + tenant + "', 'RELAY_HUB', 3.8, 11.5, 'Relais', 'YDE')");
            exec(conn, "INSERT INTO tnt_geography.relay_hubs (id, tenant_id, node_id, capacity_slots, current_occupancy, status) "
                    + "VALUES ('" + hub + "', '" + tenant + "', 'n-" + hub.toString().substring(0, 30) + "', 4, 0, 'ACTIVE')");
            insert(conn, "logistics", Map.of("id", hub));
            insert(conn, "logistics", Map.of("id", unknownHub));
            UUID client = UUID.randomUUID();
            insert(conn, "persons", Map.of("id", client));

            // A: mirrored by the former code (entry keyed by the need id, deposit packet = need id)
            UUID needA = packet(conn, UUID.randomUUID());
            UUID entryA = entry(conn, tenant, hub, needA);
            UUID depA = deposit(conn, needA, client, hub, "DELIVERED", 500.0, 0.0, "XAF", null);
            // A2: mirrored, the deposit packet is the parcel and the entry still holds the need id
            UUID parcelA2 = packet(conn, UUID.randomUUID());
            UUID needA2 = UUID.randomUUID();
            insert(conn, "delivery_needs", Map.of("id", needA2, "packet_id", parcelA2));
            UUID entryA2 = entry(conn, tenant, hub, needA2);
            deposit(conn, parcelA2, client, hub, "DELIVERED", 0.0, 0.0, "XAF", null);
            // E: an old inventory entry keyed on a delivery need (package id and tracking code), no relay deposit row
            UUID parcelE = packet(conn, UUID.randomUUID());
            UUID needE = UUID.randomUUID();
            insert(conn, "delivery_needs", Map.of("id", needE, "packet_id", parcelE));
            UUID entryE = entry(conn, tenant, hub, needE);
            // B: in progress, never mirrored
            UUID packetB = packet(conn, UUID.randomUUID());
            UUID depB = deposit(conn, packetB, client, hub, "DELIVERED", 250.5, 100.0, "XAF", null);
            // C: already retrieved, never mirrored
            UUID packetC = packet(conn, UUID.randomUUID());
            UUID depC = deposit(conn, packetC, client, hub, "RETRIEVED", 300.0, 0.0, "xaf", "2026-11-01T10:00:00Z");
            // D: hub unknown to geo
            UUID depD = deposit(conn, packet(conn, UUID.randomUUID()), client, unknownHub, "DELIVERED", 0.0, 0.0, "XAF", null);

            long entriesBefore = count(conn, "SELECT count(*) FROM tnt_hub_package_entries");

            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + entryA + "' AND owner_actor_id = '"
                    + client + "' AND storage_fee = 500.00 AND penalty_fee = 0 AND fee_currency = 'XAF' AND package_id = '" + needA
                    + "' AND picked_up_at IS NULL")).as("mirrored deposit completes its entry").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depA + "'"))
                    .as("no duplicate for a mirrored deposit").isZero();
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + entryA2 + "' AND package_id = '"
                    + parcelA2 + "' AND tracking_code = '" + parcelA2.toString().toUpperCase() + "' AND owner_actor_id = '" + client
                    + "'")).as("need id corrected to the parcel id (package and tracking code)").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + entryE + "' AND package_id = '"
                    + parcelE + "' AND tracking_code = '" + parcelE.toString().toUpperCase() + "'"))
                    .as("old entry keyed on a need re-keyed on its parcel").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries e JOIN delivery_needs dn ON dn.id = e.package_id"
                    + " WHERE dn.packet_id IS NOT NULL")).as("no entry left keyed on a need").isZero();
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depB + "' AND tenant_id = '" + tenant
                    + "' AND hub_id = '" + hub + "' AND package_id = '" + packetB + "' AND tracking_code = '"
                    + packetB.toString().toUpperCase() + "' AND storage_fee = 250.50 AND penalty_fee = 100.00 AND fee_currency = 'XAF'"
                    + " AND owner_actor_id = '" + client + "' AND picked_up_at IS NULL"))
                    .as("deposit in progress copied with the same id").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depC
                    + "' AND picked_up_at = '2026-11-01T10:00:00Z' AND picked_up_by_actor_id = '" + client + "' AND fee_currency = 'XAF'"))
                    .as("retrieved deposit copied as picked up").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depD + "'"))
                    .as("deposit of an unknown hub is not copied").isZero();
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries")).isEqualTo(entriesBefore + 2);

            assertThat(count(conn, "SELECT count(*) FROM relay_deposits rd WHERE rd.status = 'DELIVERED'"
                    + " AND EXISTS (SELECT 1 FROM tnt_geography.relay_hubs h WHERE h.id = rd.logistics_id)"
                    + " AND NOT EXISTS (SELECT 1 FROM tnt_hub_package_entries e WHERE e.hub_id = rd.logistics_id"
                    + "   AND e.picked_up_at IS NULL AND e.owner_actor_id = rd.client_id"
                    + "   AND (e.id = rd.id OR e.package_id = rd.packet_id))"))
                    .as("no deposit in progress lost").isZero();

            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE id = '" + hub
                    + "' AND current_occupancy = 4 AND status = 'FULL'")).as("occupancy recomputed").isEqualTo(1);

            long after = count(conn, "SELECT count(*) FROM tnt_hub_package_entries");
            for (String file : new String[] {"20261125_gdd_l4_412_relay_deposits_to_inventory.sql",
                    "20261125_gdd_l4_413_relay_hub_occupancy.sql"}) {
                exec(conn, java.nio.file.Files.readString(java.nio.file.Path.of(CHANGELOG + file)));
            }
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries")).as("replay is idempotent").isEqualTo(after);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.relay_hubs WHERE id = '" + hub
                    + "' AND current_occupancy = 4")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("relay capacity: hangar in its unit, occupied volume from the core parcels (cm -> m3) of active entries")
    void sufficientCapacityUsesCoreParcelsInCentimetres() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            liquibase(conn).update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);

            UUID hub = UUID.randomUUID();
            UUID tenant = UUID.randomUUID();
            Map<String, Object> rp = new LinkedHashMap<>();
            rp.put("core_relay_point_id", hub); rp.put("status", "APPROVED"); rp.put("is_active", true);
            rp.put("storage_length", 100.0); rp.put("storage_width", 100.0); rp.put("storage_height", 100.0);
            rp.put("storage_dimension_unit", "cm");                                   // hangar: 1 m3
            insert(conn, "gofp_relay_points", rp);
            UUID parcel = UUID.randomUUID();                                          // 50 x 50 x 100 cm = 0.25 m3
            insert(conn, "tnt_parcels", Map.of("id", parcel, "length_cm", 50, "width_cm", 50, "height_cm", 100));
            exec(conn, "INSERT INTO tnt_hub_package_entries (id, tenant_id, hub_id, package_id, tracking_code) VALUES ('"
                    + UUID.randomUUID() + "', '" + tenant + "', '" + hub + "', '" + parcel + "', 'CAP-1')");
            UUID gone = UUID.randomUUID();                                            // picked up: not counted
            insert(conn, "tnt_parcels", Map.of("id", gone, "length_cm", 100, "width_cm", 100, "height_cm", 100));
            exec(conn, "INSERT INTO tnt_hub_package_entries (id, tenant_id, hub_id, package_id, tracking_code, picked_up_at)"
                    + " VALUES ('" + UUID.randomUUID() + "', '" + tenant + "', '" + hub + "', '" + gone + "', 'CAP-2', now())");

            assertThat(capacityMatches(conn, hub, 0.75)).as("1 - 0.25 = 0.75 m3 left").isEqualTo(1);
            assertThat(capacityMatches(conn, hub, 0.76)).as("not 0.76 m3").isZero();
        }
    }

    private static long capacityMatches(Connection conn, UUID hub, double requiredM3) throws Exception {
        String sql = com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository
                .GofpRelayPointR2dbcRepository.SUFFICIENT_CAPACITY_SQL.replace(":requiredVolumeM3", Double.toString(requiredM3));
        return count(conn, "SELECT count(*) FROM (" + sql + ") q WHERE q.core_relay_point_id = '" + hub + "'");
    }

    private static UUID packet(Connection conn, UUID id) throws Exception {
        insert(conn, "packets", Map.of("id", id));
        return id;
    }

    private static UUID entry(Connection conn, UUID tenant, UUID hub, UUID packageId) throws Exception {
        UUID id = UUID.randomUUID();
        exec(conn, "INSERT INTO tnt_hub_package_entries (id, tenant_id, hub_id, package_id, tracking_code) VALUES ('"
                + id + "', '" + tenant + "', '" + hub + "', '" + packageId + "', '" + packageId.toString().toUpperCase() + "')");
        return id;
    }

    private static UUID deposit(Connection conn, UUID packet, UUID client, UUID hub, String status, double fee, double penalty,
                                String currency, String retrievedAt) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("packet_id", packet); row.put("client_id", client); row.put("logistics_id", hub);
        row.put("storage_fee", fee); row.put("penalty_fee", penalty); row.put("currency", currency); row.put("status", status);
        row.put("retrieved_at", retrievedAt);
        insert(conn, "relay_deposits", row);
        return id;
    }
}
