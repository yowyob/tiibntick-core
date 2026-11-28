package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Liquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.count;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.exec;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.freshDatabase;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.insert;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.migrateAsPreviousVersion;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.q;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.rerun;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.seeding;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.unrun;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.upgrade;
import static com.yowyob.tiibntick.bootstrap.config.Gdd1MigrationSupport.value;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1, parcels (architecture-cible 3.2): legacy {@code packets} rows become tnt-delivery-core parcels
 * with the same ids, the foreign keys to {@code packets} are dropped, relay deposits keyed on a delivery
 * need are re-keyed on that need's parcel; re-running changes nothing.
 */
@Tag("integration")
class ParcelMigrationIntegrationTest {

    private static final String PACKETS_SQL = "db/changelog/20261125_gdd_l1_104_packets_to_parcels.sql";
    private static final String DEPOSITS_SQL = "db/changelog/20261125_gdd_l1_106_relay_deposits_parcel_id.sql";

    private static long packetForeignKeys(Connection conn) throws Exception {
        return count(conn, """
                SELECT count(*) FROM pg_constraint c
                  JOIN pg_class t ON t.oid = c.conrelid JOIN pg_class rt ON rt.oid = c.confrelid
                 WHERE c.contype = 'f' AND rt.relname = 'packets'
                   AND t.relname IN ('delivery_needs', 'relay_deposits')""");
    }

    @Test
    @DisplayName("packets → tnt_parcels: same ids, kg/cm copied, neutral values for missing sizes, designation falls back to the title")
    void packetsBecomeParcels() throws Exception {
        try (Connection conn = freshDatabase()) {
            migrateAsPreviousVersion(conn);
            assertThat(packetForeignKeys(conn)).as("previous version: FKs to packets").isEqualTo(2);

            UUID full = UUID.randomUUID();
            UUID bare = UUID.randomUUID();
            UUID orphan = UUID.randomUUID();
            UUID alreadyThere = UUID.randomUUID();
            exec(conn, "INSERT INTO packets (id, title, designation, description, weight, width, height, length, "
                    + "thickness, is_fragile, is_perishable, photo_url) VALUES ('" + full + "', 'Title', 'Shoes', "
                    + "'A pair of shoes', 2.5, 30, 20, 12, 4, true, false, 'https://img/1.jpg')");
            exec(conn, "INSERT INTO packets (id, title, weight, width, height, length, thickness, is_fragile, "
                    + "is_perishable) VALUES ('" + bare + "', 'Only a title', NULL, 0, -3, NULL, 0, NULL, NULL)");
            exec(conn, "INSERT INTO packets (id, designation) VALUES ('" + orphan + "', 'Orphan')");
            exec(conn, "INSERT INTO packets (id, designation, weight, width, height, length) VALUES ('"
                    + alreadyThere + "', 'Legacy', 9, 9, 9, 9)");
            // A parcel with that id already exists in the core: it must not be overwritten.
            insert(conn, "tnt_parcels", Map.of("id", q(alreadyThere), "weight_kg", "3", "width_cm", "3",
                    "height_cm", "3", "length_cm", "3", "description", "'core'"));

            Liquibase upgrade = upgrade(conn);
            assertThat(unrun(upgrade)).isEmpty();

            assertThat(count(conn, "SELECT count(*) FROM tnt_parcels WHERE id IN ('" + full + "','" + bare + "','"
                    + orphan + "','" + alreadyThere + "')")).as("every packet, orphans included, keeps its id").isEqualTo(4);
            assertThat(value(conn, "SELECT designation || '|' || description || '|' || weight_kg || '|' || width_cm || '|' "
                    + "|| height_cm || '|' || length_cm || '|' || thickness_cm || '|' || fragile || '|' || perishable "
                    + "|| '|' || photo_url FROM tnt_parcels WHERE id = '" + full + "'"))
                    .isEqualTo("Shoes|A pair of shoes|2.5|30|20|12|4|true|false|https://img/1.jpg");
            assertThat(value(conn, "SELECT designation || '|' || weight_kg || '|' || width_cm || '|' || height_cm || '|' "
                    + "|| length_cm || '|' || (thickness_cm IS NULL) || '|' || fragile || '|' || perishable "
                    + "FROM tnt_parcels WHERE id = '" + bare + "'"))
                    .as("missing or non-positive sizes take 1 kg / 10 cm, a zero thickness is dropped, designation = title")
                    .isEqualTo("Only a title|1|10|10|10|true|false|false");
            assertThat(value(conn, "SELECT weight_kg || '|' || description FROM tnt_parcels WHERE id = '" + alreadyThere + "'"))
                    .as("a parcel already in the core is kept as is").isEqualTo("3|core");

            assertThat(packetForeignKeys(conn)).as("no FK to packets any more").isZero();

            long parcels = count(conn, "SELECT count(*) FROM tnt_parcels");
            rerun(conn, PACKETS_SQL);
            assertThat(count(conn, "SELECT count(*) FROM tnt_parcels")).as("re-run: nothing added").isEqualTo(parcels);
        }
    }

    @Test
    @DisplayName("a relay deposit keyed on a need is re-keyed on the need's parcel; a need without parcel keeps its id; re-run is a no-op")
    void depositsRekeyedOnTheParcel() throws Exception {
        try (Connection conn = freshDatabase()) {
            migrateAsPreviousVersion(conn);
            UUID parcel = UUID.randomUUID();
            UUID needWithParcel = UUID.randomUUID();
            UUID needWithoutParcel = UUID.randomUUID();
            UUID depositOnNeed = UUID.randomUUID();
            UUID depositOnBareNeed = UUID.randomUUID();
            UUID depositOnParcel = UUID.randomUUID();
            seeding(conn, () -> {
                exec(conn, "INSERT INTO packets (id, designation, weight, width, height, length) VALUES ('"
                        + parcel + "', 'Box', 1, 20, 20, 20)");
                insert(conn, "delivery_needs", Map.of("id", q(needWithParcel), "packet_id", q(parcel)));
                insert(conn, "delivery_needs", Map.of("id", q(needWithoutParcel)));
                // The delivery flow passed the NEED id as packetId (architecture-cible 3.2, "confusion").
                insert(conn, "relay_deposits", Map.of("id", q(depositOnNeed), "packet_id", q(needWithParcel)));
                insert(conn, "relay_deposits", Map.of("id", q(depositOnBareNeed), "packet_id", q(needWithoutParcel)));
                insert(conn, "relay_deposits", Map.of("id", q(depositOnParcel), "packet_id", q(parcel)));
            });
            long deposits = count(conn, "SELECT count(*) FROM relay_deposits");

            upgrade(conn);

            assertThat(value(conn, "SELECT packet_id FROM relay_deposits WHERE id = '" + depositOnNeed + "'"))
                    .as("need id → that need's parcel id").isEqualTo(parcel);
            assertThat(value(conn, "SELECT packet_id FROM relay_deposits WHERE id = '" + depositOnBareNeed + "'"))
                    .as("a need with no parcel: nothing to convert to").isEqualTo(needWithoutParcel);
            assertThat(value(conn, "SELECT packet_id FROM relay_deposits WHERE id = '" + depositOnParcel + "'"))
                    .isEqualTo(parcel);
            assertThat(value(conn, "SELECT packet_id FROM delivery_needs WHERE id = '" + needWithParcel + "'"))
                    .as("the need keeps the id, which is now the core parcel id").isEqualTo(parcel);
            assertThat(count(conn, "SELECT count(*) FROM tnt_parcels WHERE id = '" + parcel + "'")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM relay_deposits")).isEqualTo(deposits);

            rerun(conn, DEPOSITS_SQL);
            assertThat(value(conn, "SELECT packet_id FROM relay_deposits WHERE id = '" + depositOnNeed + "'"))
                    .as("re-run: unchanged").isEqualTo(parcel);
            assertThat(value(conn, "SELECT packet_id FROM relay_deposits WHERE id = '" + depositOnBareNeed + "'"))
                    .isEqualTo(needWithoutParcel);
        }
    }
}
