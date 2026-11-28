package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L3 (Go-Freelancer de-duplication) migration gate: a database migrated by the previous version (every
 * changeset except the {@code gdd-l3} ones), holding Go profiles with heterogeneous ids, evaluations and order
 * counts, receives the L3 changesets on restart.
 *
 * <p>Checks: profile ids aligned on the core (users on the actor id, clients on the client profile id,
 * freelancers on the core freelancer id), foreign keys still declared and valid, references repointed,
 * vetting status copied to delivery-core, evaluations converted to tp-core ratings with actor ids (duplicates
 * and unconvertible rows counted, not copied), loyalty accounts created or completed with the core
 * thresholds (H3), and every data script idempotent.
 */
@Testcontainers
@Tag("integration")
class Gdd3ProfileMigrationIntegrationTest {

    private static final String MASTER = "db/changelog/tnt-core-master.yaml";
    private static final String LOT_LABEL = "gdd-l3";
    private static final List<String> DATA_SCRIPTS = List.of(
            "db/changelog/20261125_gdd_l3_302_evaluations_to_tp_ratings.sql",
            "db/changelog/20261125_gdd_l3_303_loyalty_accounts_from_orders.sql",
            "db/changelog/20261126_gdd_l3_304_rating_averages_from_tp_ratings.sql",
            "db/changelog/20261125_gdd_l3_310_gofp_users_id_realign.sql",
            "db/changelog/20261125_gdd_l3_311_gofp_clients_id_to_client_profile.sql",
            "db/changelog/20261125_gdd_l3_312_gofp_freelancers_id_realign.sql",
            "db/changelog/20261125_gdd_l3_320_delivery_person_vetting_status.sql",
            "db/changelog/20261126_gdd_l3_331_gofreelancer_vehicles_core_reference.sql");

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("postgres")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static String createDatabase() throws Exception {
        String name = "tnt_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    private static Liquibase liquibase(Connection conn) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(conn));
        ResourceAccessor accessor = com.yowyob.tiibntick.bootstrap.config.BootstrapChangelogs.accessor();
        return new Liquibase(MASTER, accessor, database);
    }

    private static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String text(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void exec(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    /**
     * Inserts a row: the given values, and a neutral value for every other NOT NULL column without default.
     * Foreign keys are not checked here (the caller works in {@code session_replication_role = replica}).
     */
    private static void insert(Connection conn, String schema, String table, Map<String, Object> values) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        values.forEach((k, v) -> row.put(k, v == null ? "NULL" : v instanceof Number ? v.toString() : "'" + v + "'"));
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name, data_type, character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = '" + schema + "' AND table_name = '" + table + "' "
                        + "AND is_nullable = 'NO' AND column_default IS NULL")) {
            while (rs.next()) {
                String col = rs.getString(1);
                if (row.containsKey(col)) continue;
                int max = rs.getInt(3);
                String filler = "x" + UUID.randomUUID().toString().replace("-", "");
                if (max > 0 && filler.length() > max) filler = filler.substring(0, max);
                row.put(col, switch (rs.getString(2)) {
                    case "uuid" -> "'" + UUID.randomUUID() + "'";
                    case "boolean" -> "false";
                    case "integer", "bigint", "smallint", "numeric", "double precision", "real" -> "0";
                    case "timestamp without time zone", "timestamp with time zone", "date" -> "now()";
                    case "ARRAY" -> "'{}'";
                    case "jsonb", "json" -> "'{}'";
                    default -> "'" + filler + "'";
                });
            }
        }
        exec(conn, "INSERT INTO " + schema + "." + table + " (" + String.join(",", row.keySet())
                + ") VALUES (" + String.join(",", row.values()) + ")");
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static String script(String path) throws Exception {
        try (InputStream in = Gdd3ProfileMigrationIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Foreign keys declared on gofp_freelancers(id), with their validation flag. */
    private static List<String> freelancerIdForeignKeys(Connection conn) throws Exception {
        List<String> fks = new ArrayList<>();
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT c.conrelid::regclass::text || '.' || c.conname || ':' || c.convalidated "
                        + "FROM pg_constraint c WHERE c.contype = 'f' "
                        + "AND c.confrelid = 'gofp_freelancers'::regclass ORDER BY 1")) {
            while (rs.next()) fks.add(rs.getString(1));
        }
        return fks;
    }

    @Test
    @DisplayName("upgrade: heterogeneous Go profile ids, evaluations and orders move to the core ids and tables")
    void upgradeFromPreviousVersion() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // 1. the database as the previous version left it
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true); // Liquibase leaves the connection with autoCommit=false
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'tnt_tp_ratings' AND column_name = 'rating_context'"))
                    .as("previous version: no rating context yet").isZero();

            UUID tenant = UUID.randomUUID();
            // client actor A1: Go user and Go client with random ids, one client profile, 10 orders
            UUID a1 = UUID.randomUUID(), u1 = UUID.randomUUID(), g1 = UUID.randomUUID(), p1 = UUID.randomUUID();
            // freelancer actor A2: Go freelancer with a random id (H9), revoked in Go, approved in the core
            UUID a2 = UUID.randomUUID(), u2 = UUID.randomUUID(), gf2 = UUID.randomUUID(), f2 = UUID.randomUUID();
            // client actor A3: 60 orders, a loyalty account already credited with 100 points
            UUID a3 = UUID.randomUUID(), g3 = UUID.randomUUID(), p3 = UUID.randomUUID();
            // a relay point owned by A2
            UUID rp = UUID.randomUUID(), coreRp = UUID.randomUUID();
            UUID need = UUID.randomUUID(), d1 = UUID.randomUUID(), d2 = UUID.randomUUID();
            UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID(), e3 = UUID.randomUUID(), e4 = UUID.randomUUID();
            UUID e5 = UUID.randomUUID(), e6 = UUID.randomUUID();
            // freelancer actor A4: approved by the former auto-approval, PENDING in Go: keeps its approval
            UUID a4 = UUID.randomUUID(), f4 = UUID.randomUUID();
            // delivery-core rows written with the Go random freelancer id (C2)
            UUID resp = UUID.randomUUID(), coreDelivery = UUID.randomUUID();

            exec(conn, "SET session_replication_role = replica");
            insert(conn, "tnt_actor", "client_profiles", m("id", p1, "tenant_id", tenant, "actor_id", a1,
                    "created_at", "2026-01-01", "updated_at", "2026-01-01"));
            insert(conn, "tnt_actor", "client_profiles", m("id", p3, "tenant_id", tenant, "actor_id", a3,
                    "created_at", "2026-01-01", "updated_at", "2026-01-01"));
            insert(conn, "tnt_actor", "freelancer_profiles", m("id", f2, "tenant_id", tenant, "actor_id", a2,
                    "created_at", "2026-01-01", "updated_at", "2026-01-01"));
            insert(conn, "public", "tnt_delivery_persons", m("id", f2, "tenant_id", tenant, "actor_id", a2,
                    "status", "APPROVED", "logistics_type", "MOTORBIKE", "logistics_class", "STANDARD"));
            insert(conn, "tnt_actor", "freelancer_profiles", m("id", f4, "tenant_id", tenant, "actor_id", a4,
                    "created_at", "2026-01-01", "updated_at", "2026-01-01"));
            insert(conn, "public", "tnt_delivery_persons", m("id", f4, "tenant_id", tenant, "actor_id", a4,
                    "status", "APPROVED", "logistics_type", "MOTORBIKE", "logistics_class", "STANDARD"));
            insert(conn, "public", "gofp_freelancers", m("id", f4, "core_freelancer_id", f4, "core_user_id", a4,
                    "status", "PENDING"));
            insert(conn, "public", "tnt_announcement_responses", m("id", resp, "delivery_person_id", gf2,
                    "status", "PENDING"));
            insert(conn, "public", "tnt_deliveries", m("id", coreDelivery, "delivery_person_id", gf2,
                    "sender_id", a1, "status", "IN_TRANSIT"));
            // vehicles (H10): a fictitious core reference with a plate, one without plate, one really delegated
            UUID veh1 = UUID.randomUUID(), veh2 = UUID.randomUUID(), veh3 = UUID.randomUUID(), coreVeh = UUID.randomUUID();
            insert(conn, "public", "tnt_freelancer_vehicles", m("id", coreVeh, "freelancer_org_id", UUID.randomUUID(),
                    "vehicle_type", "MOTORCYCLE", "plate_number", "LT 900", "max_capacity_kg", 40));
            insert(conn, "public", "gofreelancer_vehicles", m("id", veh1, "freelancer_id", gf2,
                    "core_vehicle_id", UUID.randomUUID(), "vehicle_type", "MOTORBIKE", "plate_number", "LT 1"));
            insert(conn, "public", "gofreelancer_vehicles", m("id", veh2, "freelancer_id", f4,
                    "core_vehicle_id", UUID.randomUUID()));
            insert(conn, "public", "gofreelancer_vehicles", m("id", veh3, "freelancer_id", UUID.randomUUID(),
                    "core_vehicle_id", coreVeh, "vehicle_type", "MOTORBIKE", "plate_number", "LT 900"));
            insert(conn, "public", "gofp_users", m("id", u1, "core_user_id", a1));
            insert(conn, "public", "gofp_users", m("id", u2, "core_user_id", a2));
            insert(conn, "public", "gofp_users", m("id", a3, "core_user_id", a3)); // already aligned
            insert(conn, "public", "gofp_clients", m("id", g1, "core_client_id", p1, "core_user_id", a1,
                    "total_orders", 10, "loyalty_status", "SILVER"));
            insert(conn, "public", "gofp_clients", m("id", g3, "core_client_id", UUID.randomUUID(), "core_user_id", a3,
                    "total_orders", 60, "loyalty_status", "PLATINUM"));
            insert(conn, "public", "gofp_freelancers", m("id", gf2, "core_freelancer_id", f2, "core_user_id", a2,
                    "status", "REVOKED"));
            insert(conn, "public", "gofp_relay_points", m("id", rp, "core_relay_point_id", coreRp,
                    "core_freelancer_id", f2));
            insert(conn, "public", "delivery_needs", m("id", need, "assigned_freelancer_id", gf2));
            insert(conn, "public", "gofp_need_invitations", m("delivery_need_id", need, "freelancer_id", gf2,
                    "invited_by", a1, "status", "OPEN"));
            insert(conn, "public", "tnt_loyalty_accounts", m("tenant_id", tenant, "third_party_id", a3,
                    "available_points", 80, "lifetime_points", 100, "redeemed_points", 20));
            // evaluations, written with the Go ids
            insert(conn, "public", "evaluations", m("id", e1, "delivery_id", d1, "evaluator_id", g1,
                    "evaluated_id", gf2, "rating", 4, "comment", "fast", "type", "CLIENT_TO_DP",
                    "created_at", "2026-01-01 10:00:00"));
            insert(conn, "public", "evaluations", m("id", e2, "delivery_id", d1, "evaluator_id", g1,
                    "evaluated_id", gf2, "rating", 2, "type", "CLIENT_RATING_DELIVERY_PERSON",
                    "created_at", "2026-01-02 10:00:00")); // later duplicate in the core: the oldest is kept
            insert(conn, "public", "evaluations", m("id", e3, "delivery_id", d2, "evaluator_id", UUID.randomUUID(),
                    "evaluated_id", gf2, "rating", 5, "type", "CLIENT_TO_DP")); // unknown evaluator
            insert(conn, "public", "evaluations", m("id", e4, "delivery_id", d2, "evaluator_id", g1,
                    "evaluated_id", coreRp, "rating", 3, "type", "CLIENT_TO_RP"));
            // same delivery d1, same rater as e1, another kind of party: kept (C3)
            insert(conn, "public", "evaluations", m("id", e5, "delivery_id", d1, "evaluator_id", g1,
                    "evaluated_id", rp, "rating", 5, "type", "CLIENT_TO_RP", "created_at", "2026-01-03 10:00:00"));
            // the courier rates the client of d1
            insert(conn, "public", "evaluations", m("id", e6, "delivery_id", d1, "evaluator_id", gf2,
                    "evaluated_id", g1, "rating", 3, "type", "DP_TO_CLIENT"));
            exec(conn, "SET session_replication_role = origin");

            // diagnostic before migration (H9)
            assertThat(count(conn, "SELECT count(*) FROM gofp_users WHERE id <> core_user_id")).isEqualTo(2);
            assertThat(count(conn, "SELECT count(*) FROM gofp_freelancers WHERE id <> core_freelancer_id")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_tnt_tp_rating_mission_rater_kind'"))
                    .as("previous version: one rating per (mission, rater)").isZero();
            List<String> fksBefore = freelancerIdForeignKeys(conn);
            assertThat(fksBefore).isNotEmpty();
            long evaluationsBefore = count(conn, "SELECT count(*) FROM evaluations");

            // 2. the application restarts on the new version
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            assertMigrated(conn, tenant, a1, a2, a3, p1, g3, f2, need, d1, d2, e1, e2, e3, e4, fksBefore, evaluationsBefore,
                    e5, e6, f4, rp, resp, coreDelivery);
            assertVehicles(conn, veh1, veh2, veh3, coreVeh, f2);

            // 3. every data script is idempotent: a second run changes nothing
            for (String path : DATA_SCRIPTS) {
                exec(conn, script(path));
            }
            assertMigrated(conn, tenant, a1, a2, a3, p1, g3, f2, need, d1, d2, e1, e2, e3, e4, fksBefore, evaluationsBefore,
                    e5, e6, f4, rp, resp, coreDelivery);
            assertVehicles(conn, veh1, veh2, veh3, coreVeh, f2);
        }
    }

    /** H10: fictitious core references reset, the real one kept; personal vehicles possible in the core. */
    private static void assertVehicles(Connection conn, UUID veh1, UUID veh2, UUID veh3, UUID coreVeh, UUID f2)
            throws Exception {
        assertThat(text(conn, "SELECT coalesce(core_vehicle_id::text, 'pending') || '/' || freelancer_id "
                + "FROM gofreelancer_vehicles WHERE id = '" + veh1 + "'")).isEqualTo("pending/" + f2);
        assertThat(text(conn, "SELECT coalesce(core_vehicle_id::text, 'pending') FROM gofreelancer_vehicles "
                + "WHERE id = '" + veh2 + "'")).isEqualTo("pending");
        assertThat(text(conn, "SELECT core_vehicle_id::text FROM gofreelancer_vehicles WHERE id = '" + veh3 + "'"))
                .isEqualTo(coreVeh.toString());
        assertThat(text(conn, "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'tnt_freelancer_vehicles' AND column_name = 'freelancer_org_id'")).isEqualTo("YES");
        assertThat(text(conn, "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'tnt_freelancer_vehicles' AND column_name = 'max_capacity_kg'")).isEqualTo("NO");
        assertThat(count(conn, "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_tnt_fl_vehicles_owner'"))
                .isEqualTo(1);
        UUID actor = UUID.randomUUID(), personal = UUID.randomUUID();
        exec(conn, "INSERT INTO tnt_freelancer_vehicles (id, owner_actor_id, vehicle_type, plate_number, max_capacity_kg) "
                + "VALUES ('" + personal + "', '" + actor + "', 'SCOOTER', 'P-" + personal + "', 20)");
        exec(conn, "DELETE FROM tnt_freelancer_vehicles WHERE id = '" + personal + "'");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> exec(conn, "INSERT INTO tnt_freelancer_vehicles "
                        + "(id, vehicle_type, plate_number, max_capacity_kg) VALUES ('" + UUID.randomUUID()
                        + "', 'CAR', 'NO-OWNER', 10)"))
                .as("a vehicle without any owner is refused").isInstanceOf(java.sql.SQLException.class);
    }

    private static void assertMigrated(Connection conn, UUID tenant, UUID a1, UUID a2, UUID a3, UUID p1, UUID g3,
                                       UUID f2, UUID need, UUID d1, UUID d2, UUID e1, UUID e2, UUID e3, UUID e4,
                                       List<String> fksBefore, long evaluationsBefore,
                                       UUID e5, UUID e6, UUID f4, UUID rp, UUID resp, UUID coreDelivery) throws Exception {
        // ids: users on the actor id, clients on the client profile id, freelancers on the core freelancer id
        assertThat(count(conn, "SELECT count(*) FROM gofp_users WHERE id <> core_user_id")).isZero();
        assertThat(count(conn, "SELECT count(*) FROM gofp_users")).isEqualTo(3);
        assertThat(count(conn, "SELECT count(*) FROM gofp_clients WHERE id = '" + p1 + "' AND core_user_id = '" + a1 + "'"))
                .as("client realigned on its client profile (matched by core_client_id)").isEqualTo(1);
        assertThat(count(conn, "SELECT count(*) FROM gofp_clients WHERE core_user_id = '" + a3 + "' AND id = "
                + "(SELECT id FROM tnt_actor.client_profiles WHERE actor_id = '" + a3 + "')"))
                .as("client realigned on the single client profile of its actor").isEqualTo(1);
        assertThat(count(conn, "SELECT count(*) FROM gofp_clients WHERE id = '" + g3 + "'")).isZero();
        assertThat(count(conn, "SELECT count(*) FROM gofp_freelancers WHERE id <> core_freelancer_id")).isZero();

        // foreign keys intact (same constraints, still validated) and references repointed
        assertThat(freelancerIdForeignKeys(conn)).isEqualTo(fksBefore);
        assertThat(text(conn, "SELECT assigned_freelancer_id FROM delivery_needs WHERE id = '" + need + "'"))
                .isEqualTo(f2.toString());
        assertThat(text(conn, "SELECT freelancer_id FROM gofp_need_invitations WHERE delivery_need_id = '" + need + "'"))
                .isEqualTo(f2.toString());

        // delivery-core copies of the Go freelancer id repointed (C2)
        assertThat(text(conn, "SELECT delivery_person_id FROM tnt_announcement_responses WHERE id = '" + resp + "'"))
                .isEqualTo(f2.toString());
        assertThat(text(conn, "SELECT delivery_person_id FROM tnt_deliveries WHERE id = '" + coreDelivery + "'"))
                .isEqualTo(f2.toString());

        // vetting status now held by delivery-core; an approved delivery person is never demoted
        assertThat(text(conn, "SELECT status FROM tnt_delivery_persons WHERE id = '" + f2 + "'")).isEqualTo("REVOKED");
        assertThat(text(conn, "SELECT status FROM tnt_delivery_persons WHERE id = '" + f4 + "'")).isEqualTo("APPROVED");

        // ratings: actor ids, mission = delivery, context = type, one per (mission, rater, kind)
        assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE id = '" + e1 + "' AND tenant_id = '" + tenant
                + "' AND rated_third_party_id = '" + a2 + "' AND rater_actor_id = '" + a1 + "' AND mission_id = '" + d1
                + "' AND score = 4 AND comment = 'fast' AND rating_context = 'CLIENT_TO_DP' AND subject_kind = 'DELIVERY_PERSON'"))
                .isEqualTo(1);
        assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE id = '" + e2 + "'"))
                .as("second rating of the same kind of party, same mission and rater: skipped").isZero();
        assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE id = '" + e5 + "' AND rater_actor_id = '" + a1
                + "' AND mission_id = '" + d1 + "' AND subject_kind = 'RELAY_OPERATOR'"))
                .as("CLIENT_TO_RP of the delivery already rated CLIENT_TO_DP by the same client: kept (C3)").isEqualTo(1);
        assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE id = '" + e6 + "' AND rated_third_party_id = '"
                + a1 + "' AND rater_actor_id = '" + a2 + "' AND subject_kind = 'CLIENT'")).isEqualTo(1);

        // averages recomputed from the core ratings (304): freelancer 4 (e1), client 3 (e6), relay (3 + 5) / 2
        assertThat(text(conn, "SELECT rating_score || '/' || rating_total FROM tnt_actor.freelancer_profiles WHERE id = '"
                + f2 + "'")).isEqualTo("4/1");
        assertThat(text(conn, "SELECT rating_score || '/' || rating_total FROM tnt_actor.client_profiles WHERE id = '"
                + p1 + "'")).isEqualTo("3/1");
        assertThat(text(conn, "SELECT rating FROM gofp_relay_points WHERE id = '" + rp + "'")).isEqualTo("4");
        assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE id = '" + e3 + "'"))
                .as("unknown evaluator: not converted").isZero();
        assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE id = '" + e4 + "' AND rated_third_party_id = '"
                + a2 + "' AND mission_id = '" + d2 + "' AND subject_kind = 'RELAY_OPERATOR'"))
                .as("relay point rating kept on its owner with the relay subject").isEqualTo(1);
        assertThat(count(conn, "SELECT count(*) FROM evaluations")).as("legacy rows kept until the closure")
                .isEqualTo(evaluationsBefore);

        // loyalty (H3): 10 orders = 100 points = BRONZE (was SILVER in Go); 60 orders = 600 points = SILVER,
        // an existing account is completed without double counting
        assertThat(text(conn, "SELECT lifetime_points || '/' || available_points || '/' || current_tier "
                + "FROM tnt_loyalty_accounts WHERE tenant_id = '" + tenant + "' AND third_party_id = '" + a1 + "'"))
                .isEqualTo("100/100/BRONZE");
        assertThat(text(conn, "SELECT lifetime_points || '/' || available_points || '/' || redeemed_points || '/' || current_tier "
                + "FROM tnt_loyalty_accounts WHERE tenant_id = '" + tenant + "' AND third_party_id = '" + a3 + "'"))
                .isEqualTo("600/580/20/SILVER");
        assertThat(count(conn, "SELECT count(*) FROM tnt_loyalty_accounts")).isEqualTo(2);
    }
}
