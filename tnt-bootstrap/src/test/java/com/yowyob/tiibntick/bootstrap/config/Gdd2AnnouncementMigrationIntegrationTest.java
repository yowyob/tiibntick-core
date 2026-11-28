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
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd2MigrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L2 upgrade gate — announcements: a database migrated by the previous version (every changeset
 * except the lot's), holding a core announcement and its gofp mirror row, receives the lot's changesets
 * on restart: the core columns are added, filled from the mirror (names folded, need link → origin
 * reference), the orphan mirror rows are counted and left alone, a second run changes nothing.
 */
@Testcontainers
@Tag("integration")
class Gdd2AnnouncementMigrationIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("postgres")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @Test
    @DisplayName("upgrade: core announcement filled from the mirror, orphans counted, idempotent, one announcement per origin")
    void upgradeFromPreviousVersion() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // 1. the production database as the previous version left it
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            int lotChangesets = previous.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final")).size();
            assertThat(lotChangesets).as("the lot's changesets only (201, 203 core; 202, 204, 210, 211 gofp)").isEqualTo(6);
            conn.setAutoCommit(true);
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns WHERE table_name = "
                    + "'tnt_delivery_announcements' AND column_name = 'origin_ref_id'")).isZero();

            UUID tenant = UUID.randomUUID();
            UUID parcel = UUID.randomUUID();
            UUID linked = UUID.randomUUID();
            UUID plain = UUID.randomUUID();
            UUID orphan = UUID.randomUUID();
            UUID need = UUID.randomUUID();
            UUID hub = UUID.randomUUID();
            insert(conn, "tnt_parcels", cols("id", parcel));
            for (UUID id : new UUID[]{linked, plain}) {
                insert(conn, "tnt_delivery_announcements", cols("id", id, "tenant_id", tenant, "parcel_id", parcel,
                        "status", "PUBLISHED", "pricing_mode", "QUOTE_REQUEST", "urgency", "STANDARD"));
            }
            insert(conn, "announcements", cols("id", linked, "shipper_first_name", "Jean", "shipper_last_name", "Mbarga Essomba",
                    "shipper_email", "jean@example.org", "shipper_phone", "+237690000001",
                    "recipient_email", "marie@example.org", "payment_method", "CASH", "transport_method", "ROAD",
                    "required_vehicle_type", "MOTORBIKE", "distance", 12.5, "duration", 45,
                    "destination_relay_point_id", hub, "signature_url", "sig://abc", "logistics_price", 300.0,
                    "delivery_need_id", need));
            insert(conn, "announcements", cols("id", plain, "payment_method", "WALLET"));
            insert(conn, "announcements", cols("id", orphan, "payment_method", "CASH"));
            long mirrorRows = count(conn, "SELECT count(*) FROM announcements");
            long appliedBefore = count(conn, "SELECT count(*) FROM databasechangelog");

            // 2. restart on the new version
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + lotChangesets);

            String row = "FROM tnt_delivery_announcements WHERE id = '" + linked + "'";
            assertThat(string(conn, "SELECT sender_name " + row)).isEqualTo("Jean Mbarga Essomba");
            assertThat(string(conn, "SELECT sender_email " + row)).isEqualTo("jean@example.org");
            assertThat(string(conn, "SELECT sender_phone " + row)).isEqualTo("+237690000001");
            assertThat(string(conn, "SELECT recipient_email " + row)).isEqualTo("marie@example.org");
            assertThat(string(conn, "SELECT payment_method " + row)).isEqualTo("CASH");
            assertThat(string(conn, "SELECT transport_method " + row)).isEqualTo("ROAD");
            assertThat(string(conn, "SELECT required_vehicle_type " + row)).isEqualTo("MOTORBIKE");
            assertThat(string(conn, "SELECT distance_km " + row)).isEqualTo("12.500");
            assertThat(string(conn, "SELECT estimated_duration_min " + row)).isEqualTo("45");
            assertThat(string(conn, "SELECT destination_hub_id " + row)).isEqualTo(hub.toString());
            assertThat(string(conn, "SELECT handover_signature_ref " + row)).isEqualTo("sig://abc");
            assertThat(string(conn, "SELECT service_fee " + row)).isEqualTo("300.00");
            assertThat(string(conn, "SELECT origin_ref_type " + row)).isEqualTo("DELIVERY_NEED");
            assertThat(string(conn, "SELECT origin_ref_id " + row)).isEqualTo(need.toString());

            String plainRow = "FROM tnt_delivery_announcements WHERE id = '" + plain + "'";
            assertThat(string(conn, "SELECT payment_method " + plainRow)).isEqualTo("WALLET");
            assertThat(string(conn, "SELECT origin_ref_id " + plainRow)).as("no need, no origin").isNull();
            assertThat(string(conn, "SELECT sender_name " + plainRow)).isNull();

            // orphan mirror rows: counted (report), untouched
            assertThat(count(conn, "SELECT count(*) FROM announcements l WHERE NOT EXISTS "
                    + "(SELECT 1 FROM tnt_delivery_announcements a WHERE a.id = l.id)")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM announcements")).isEqualTo(mirrorRows);
            assertThat(string(conn, "SELECT is_nullable FROM information_schema.columns WHERE table_name = "
                    + "'announcements' AND column_name = 'pickup_address_id'")).isEqualTo("YES");

            // 3. one announcement per origin
            insert(conn, "tnt_delivery_announcements", cols("id", UUID.randomUUID(), "tenant_id", tenant, "parcel_id", parcel,
                    "status", "PUBLISHED", "pricing_mode", "QUOTE_REQUEST", "urgency", "STANDARD"));
            assertThatThrownBy(() -> insert(conn, "tnt_delivery_announcements", cols("id", UUID.randomUUID(),
                    "tenant_id", tenant, "parcel_id", parcel, "status", "PUBLISHED", "pricing_mode", "QUOTE_REQUEST",
                    "urgency", "STANDARD", "origin_ref_type", "DELIVERY_NEED", "origin_ref_id", need)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_tnt_ann_origin_ref");

            // 4. a further restart applies nothing
            Liquibase again = liquibase(conn);
            again.validate();
            again.update(new Contexts(), new LabelExpression("!gdd-final"));
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + lotChangesets);
        }
    }
}
