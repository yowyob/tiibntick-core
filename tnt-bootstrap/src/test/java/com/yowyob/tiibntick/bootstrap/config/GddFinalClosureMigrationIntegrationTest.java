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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Closure of the Go-Freelancer deduplication (gdd-final, D-154): on a database migrated by the previous version
 * (everything but the closure, demo fixtures included), the legacy tables and columns are dropped when every legacy
 * row has its core counterpart; a single row without one stops the migration (HALT) before anything is dropped.
 */
@Testcontainers
@Tag("integration")
class GddFinalClosureMigrationIntegrationTest {

    private static final String CLOSURE = "gdd-final";

    private static final List<String> DROPPED_TABLES = List.of("announcements", "responses", "packets", "evaluations",
            "payments", "relay_deposits", "person_addresses", "addresses", "notifications", "password_tokens",
            "delivery_person_pricing", "users", "persons", "clients", "delivery_persons", "logistics");

    private static final List<String> KEPT_TABLES = List.of("deliveries", "delivery_needs", "gofp_users", "gofp_clients",
            "gofp_freelancers", "gofreelancer_vehicles", "announcement_subscriptions", "comments", "subscriptions",
            "freelancer_eligibility", "logistics_pricing", "relay_point_subscriptions");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    @Test
    @DisplayName("every legacy row has its core counterpart: legacy tables and columns dropped, live tables and rows kept, re-run is a no-op")
    void coveredDatabaseIsClosed() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            liquibase(conn).update(new Contexts(), new LabelExpression("!" + CLOSURE));
            conn.setAutoCommit(true);
            assertThat(count(conn, "SELECT count(*) FROM persons")).as("demo fixtures present before").isPositive();

            UUID tenant = UUID.randomUUID();
            UUID actor = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor, "core_user_id", actor));
            // a legacy person of a known actor, and a live comment pointing at it
            UUID person = UUID.randomUUID();
            insert(conn, "users", Map.of("id", actor));
            insert(conn, "persons", Map.of("id", person, "user_id", actor, "email", "p" + person + "@test"));
            UUID comment = UUID.randomUUID();
            insert(conn, "comments", Map.of("id", comment, "person_id", person, "person_receiver_id", person));
            // a gofp delivery whose agreed terms live in the core delivery of the same id
            UUID delivery = UUID.randomUUID();
            insert(conn, "tnt_deliveries", Map.of("id", delivery, "tenant_id", tenant));
            insert(conn, "deliveries", Map.of("id", delivery, "tarif", 1500.0, "status", "DELIVERED"));
            // a need assigned before lot L2 whose delivery exists in the core
            UUID announcement = UUID.randomUUID();
            insert(conn, "tnt_delivery_announcements", Map.of("id", announcement, "tenant_id", tenant,
                    "created_delivery_id", delivery));
            UUID need = UUID.randomUUID();
            insert(conn, "delivery_needs", Map.of("id", need, "user_id", actor, "status", "ASSIGNED",
                    "announcement_id", announcement));
            // a legacy announcement mirrored in the core, a packet copied as a parcel
            insert(conn, "announcements", Map.of("id", announcement));
            UUID parcel = UUID.randomUUID();
            insert(conn, "tnt_parcels", Map.of("id", parcel));
            insert(conn, "packets", Map.of("id", parcel));

            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression());
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();

            for (String table : DROPPED_TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = '" + table + "'")).as("dropped: " + table).isZero();
            }
            for (String table : KEPT_TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = '" + table + "'")).as("kept: " + table).isEqualTo(1);
            }
            assertThat(columns(conn, "deliveries", "status", "freelancer_id", "tarif", "pickup_min_time", "pickup_max_time",
                    "delivery_min_time", "delivery_max_time", "actual_pickup_time", "actual_delivery_time")).isZero();
            assertThat(columns(conn, "delivery_needs", "packet_id", "status", "delivery_id", "assigned_freelancer_id")).isZero();
            assertThat(columns(conn, "gofp_users", "password_hash")).isZero();
            assertThat(columns(conn, "gofp_clients", "loyalty_status", "total_orders", "rating")).isZero();
            assertThat(columns(conn, "gofp_freelancers", "rating")).isZero();
            assertThat(columns(conn, "gofp_freelancers", "total_deliveries", "remaining_deliveries", "latitude_gps"))
                    .as("courier counters and position kept").isEqualTo(3);
            assertThat(columns(conn, "deliveries", "announcement_id", "delivery_need_id", "duration")).isEqualTo(3);

            assertThat(count(conn, "SELECT count(*) FROM comments WHERE id = '" + comment + "'")).as("live row kept").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM deliveries WHERE id = '" + delivery + "'")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM delivery_needs WHERE id = '" + need + "' AND announcement_id = '"
                    + announcement + "'")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_deliveries WHERE id = '" + delivery + "'")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM pg_proc WHERE proname LIKE 'gdd\\_final\\_%'"))
                    .as("census functions removed").isZero();
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog WHERE author = 'gdd-final' AND labels = 'gdd-final'"))
                    .isEqualTo(9);

            Liquibase again = liquibase(conn);
            again.update(new Contexts(), new LabelExpression());
            assertThat(again.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
        }
    }

    @Test
    @DisplayName("a legacy row without counterpart halts the closure before any drop; once covered, the closure passes")
    void uncoveredRowHaltsTheClosure() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            liquibase(conn).update(new Contexts(), new LabelExpression("!" + CLOSURE));
            conn.setAutoCommit(true);
            UUID stranger = UUID.randomUUID();
            insert(conn, "persons", Map.of("id", stranger, "email", "s" + stranger + "@test"));

            assertThatThrownBy(() -> liquibase(conn).update(new Contexts(), new LabelExpression()))
                    .hasStackTraceContaining("gdd_final_uncovered");
            conn.setAutoCommit(true);

            for (String table : DROPPED_TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = '" + table + "'")).as("still there: " + table).isEqualTo(1);
            }
            assertThat(columns(conn, "deliveries", "tarif")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog WHERE id LIKE 'gdd-final-9%' AND id <> "
                    + "'gdd-final-901-closure-census'")).as("no drop recorded").isZero();
            assertThat(count(conn, "SELECT uncovered FROM public.gdd_final_uncovered() WHERE item = 'persons'"))
                    .as("diagnostic names the table").isEqualTo(1);

            // the maintainer links the person to its actor (or removes it): the closure then passes
            UUID actor = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor, "core_user_id", actor));
            insert(conn, "users", Map.of("id", actor));
            exec(conn, "UPDATE persons SET user_id = '" + actor + "' WHERE id = '" + stranger + "'");

            Liquibase upgrade = liquibase(conn);
            upgrade.update(new Contexts(), new LabelExpression());
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
            assertThat(count(conn, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                    + "WHERE n.nspname = 'public' AND c.relname = 'persons'")).isZero();
        }
    }

    private static long columns(Connection conn, String table, String... names) throws Exception {
        return count(conn, "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '"
                + table + "' AND column_name IN ('" + String.join("', '", names) + "')");
    }
}
