package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Liquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
 * Lot L1, deliveries (architecture-cible 3.1): on upgrade, the core gets the agreed price, currency and
 * windows of every gofp delivery it knows (same id), the gofp row keeps its columns (removed at closure,
 * H7) but no longer has to carry the windows; nothing about the status is migrated (the core already owns
 * it). Additive, validated, idempotent.
 */
@Tag("integration")
class Gdd1DeliveryMigrationIntegrationTest {

    private static final String TERMS_SQL = "db/changelog/20261125_gdd_l1_107_deliveries_agreed_terms_to_core.sql";
    private static final List<String> LOT_CHANGESETS = List.of(
            "gdd-l1-101-parcel-designation-thickness", "gdd-l1-102-parcel-description-text",
            "gdd-l1-110-delivery-agreed-terms", "gdd-l1-103-deliveries-extension-nullable-terms",
            "gdd-l1-104-packets-to-parcels", "gdd-l1-105-drop-packet-foreign-keys",
            "gdd-l1-106-relay-deposits-parcel-id", "gdd-l1-107-deliveries-agreed-terms-to-core");

    private static void coreDelivery(Connection conn, UUID id, UUID parcelId, UUID announcementId) throws Exception {
        insert(conn, "tnt_deliveries", Map.of("id", q(id), "parcel_id", q(parcelId), "announcement_id",
                q(announcementId), "status", "'CREATED'", "urgency", "'STANDARD'"));
    }

    private static void gofpDelivery(Connection conn, UUID id, String tarif, String pickupMin) throws Exception {
        exec(conn, "INSERT INTO deliveries (id, tarif, status, pickup_min_time, pickup_max_time, delivery_min_time, "
                + "delivery_max_time) VALUES ('" + id + "', " + tarif + ", 'IN_TRANSIT', " + pickupMin
                + ", TIMESTAMP '2026-11-01 12:00:00', TIMESTAMP '2026-11-01 13:00:00', TIMESTAMP '2026-11-02 08:00:00')");
    }

    private static String nullability(Connection conn, String column) throws Exception {
        return (String) value(conn, "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'deliveries' AND column_name = '" + column + "'");
    }

    @Test
    @DisplayName("upgrade: price, currency and UTC windows land on the core delivery with the same id; orphans counted, rows kept")
    void agreedTermsMoveToTheCore() throws Exception {
        try (Connection conn = freshDatabase()) {
            migrateAsPreviousVersion(conn);
            assertThat(nullability(conn, "pickup_min_time")).as("previous version").isEqualTo("NO");
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'tnt_deliveries' AND column_name = 'agreed_amount'")).isZero();

            UUID parcel = UUID.randomUUID();
            UUID announcement = UUID.randomUUID();
            UUID known = UUID.randomUUID();
            UUID negative = UUID.randomUUID();
            UUID orphan = UUID.randomUUID();
            seeding(conn, () -> {
                insert(conn, "tnt_parcels", Map.of("id", q(parcel), "weight_kg", "1", "width_cm", "10",
                        "height_cm", "10", "length_cm", "10"));
                insert(conn, "tnt_delivery_announcements", Map.of("id", q(announcement), "currency", "'XAF'"));
                coreDelivery(conn, known, parcel, announcement);
                coreDelivery(conn, negative, parcel, null);
                gofpDelivery(conn, known, "3000", "TIMESTAMP '2026-11-01 10:00:00'");
                gofpDelivery(conn, negative, "-5", "TIMESTAMP '2026-11-01 10:00:00'");
                gofpDelivery(conn, orphan, "1500", "TIMESTAMP '2026-11-01 10:00:00'");
            });
            long gofpRows = count(conn, "SELECT count(*) FROM deliveries");
            long applied = count(conn, "SELECT count(*) FROM databasechangelog");

            Liquibase upgrade = upgrade(conn);

            assertThat(unrun(upgrade)).isEmpty();
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(applied + LOT_CHANGESETS.size());
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog WHERE author = 'gdd-l1' AND labels = 'gdd-l1'"))
                    .as("ids, author and label of the lot").isEqualTo(LOT_CHANGESETS.size());
            for (String id : LOT_CHANGESETS) {
                assertThat(count(conn, "SELECT count(*) FROM databasechangelog WHERE id = '" + id + "'")).as(id).isEqualTo(1);
            }

            assertThat(value(conn, "SELECT agreed_amount FROM tnt_deliveries WHERE id = '" + known + "'").toString())
                    .isEqualTo("3000.00");
            assertThat(value(conn, "SELECT agreed_currency FROM tnt_deliveries WHERE id = '" + known + "'"))
                    .as("currency of the originating core announcement").isEqualTo("XAF");
            assertThat(typed(conn, "SELECT pickup_window_start FROM tnt_deliveries WHERE id = '" + known + "'", OffsetDateTime.class))
                    .as("legacy TIMESTAMP read as UTC")
                    .isEqualTo(OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC));
            assertThat(typed(conn, "SELECT delivery_window_end FROM tnt_deliveries WHERE id = '" + known + "'", OffsetDateTime.class))
                    .isEqualTo(OffsetDateTime.of(2026, 11, 2, 8, 0, 0, 0, ZoneOffset.UTC));
            assertThat(value(conn, "SELECT agreed_amount FROM tnt_deliveries WHERE id = '" + negative + "'"))
                    .as("a negative price is not copied").isNull();
            assertThat(value(conn, "SELECT agreed_currency FROM tnt_deliveries WHERE id = '" + negative + "'"))
                    .as("no announcement: no currency").isNull();
            assertThat(value(conn, "SELECT status FROM tnt_deliveries WHERE id = '" + known + "'"))
                    .as("the core status is never touched by the migration").isEqualTo("CREATED");

            assertThat(count(conn, "SELECT count(*) FROM deliveries g WHERE NOT EXISTS "
                    + "(SELECT 1 FROM tnt_deliveries d WHERE d.id = g.id)"))
                    .as("gofp deliveries with no core counterpart (left as they are)").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM deliveries")).as("gofp rows kept").isEqualTo(gofpRows);
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns WHERE table_name = 'deliveries' "
                    + "AND column_name IN ('status','freelancer_id','tarif','pickup_min_time','pickup_max_time',"
                    + "'delivery_min_time','delivery_max_time','actual_pickup_time','actual_delivery_time')"))
                    .as("no column removed before the closure changeset (H7)").isEqualTo(9);
            for (String column : List.of("pickup_min_time", "pickup_max_time", "delivery_min_time", "delivery_max_time")) {
                assertThat(nullability(conn, column)).as(column).isEqualTo("YES");
            }

            // An extension row with no window can now be written (what the application does after L1).
            exec(conn, "INSERT INTO deliveries (id) VALUES ('" + UUID.randomUUID() + "')");
        }
    }

    @Test
    @DisplayName("re-run: a value already recorded in the core is never overwritten")
    void rerunKeepsCoreValues() throws Exception {
        try (Connection conn = freshDatabase()) {
            migrateAsPreviousVersion(conn);
            UUID parcel = UUID.randomUUID();
            UUID id = UUID.randomUUID();
            seeding(conn, () -> {
                insert(conn, "tnt_parcels", Map.of("id", q(parcel), "weight_kg", "1", "width_cm", "10",
                        "height_cm", "10", "length_cm", "10"));
                coreDelivery(conn, id, parcel, null);
                gofpDelivery(conn, id, "2000", "TIMESTAMP '2026-11-01 10:00:00'");
            });
            upgrade(conn);
            assertThat(value(conn, "SELECT agreed_amount FROM tnt_deliveries WHERE id = '" + id + "'").toString())
                    .isEqualTo("2000.00");

            // The price is renegotiated in the core after the upgrade; the gofp column still says 2000.
            exec(conn, "UPDATE tnt_deliveries SET agreed_amount = 2500 WHERE id = '" + id + "'");
            rerun(conn, TERMS_SQL);
            assertThat(value(conn, "SELECT agreed_amount FROM tnt_deliveries WHERE id = '" + id + "'").toString())
                    .isEqualTo("2500.00");
        }
    }

    private static <T> T typed(Connection conn, String sql, Class<T> type) throws Exception {
        try (var s = conn.createStatement(); var rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getObject(1, type) : null;
        }
    }
}
