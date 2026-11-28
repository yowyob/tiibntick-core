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
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd2MigrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2 upgrade gate — delivery needs: on a database migrated by the previous version, a published need
 * and an unpublished one get {@code parcel_id} (= {@code packet_id}, same UUID) and {@code announcement_id}
 * (from the mirror link); {@code user_id} and the product data are unchanged; {@code status} is no longer
 * required (derived from the core).
 */
@Testcontainers
@Tag("integration")
class Gdd2NeedMigrationIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("postgres")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @Test
    @DisplayName("upgrade: packet_id → parcel_id, announcement_id from the mirror, user_id unchanged, status optional")
    void upgradeFromPreviousVersion() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);

            UUID user = UUID.randomUUID();
            UUID pickup = UUID.randomUUID();
            UUID drop = UUID.randomUUID();
            UUID packet = UUID.randomUUID();
            UUID published = UUID.randomUUID();
            UUID unpublished = UUID.randomUUID();
            UUID announcement = UUID.randomUUID();
            insert(conn, "users", cols("id", user));
            insert(conn, "addresses", cols("id", pickup));
            insert(conn, "addresses", cols("id", drop));
            insert(conn, "packets", cols("id", packet));
            insert(conn, "delivery_needs", cols("id", published, "user_id", user, "packet_id", packet,
                    "pickup_address_id", pickup, "delivery_address_id", drop, "title", "Colis", "status", "PENDING",
                    "pickup_deadline", "2026-12-01 10:00:00", "requested_storage_days", 3));
            insert(conn, "delivery_needs", cols("id", unpublished, "user_id", user, "packet_id", null,
                    "pickup_address_id", pickup, "delivery_address_id", drop, "title", "Sans colis", "status", "PENDING"));
            insert(conn, "announcements", cols("id", announcement, "delivery_need_id", published));

            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            String pub = "FROM delivery_needs WHERE id = '" + published + "'";
            assertThat(string(conn, "SELECT parcel_id " + pub)).as("same UUID as the packet").isEqualTo(packet.toString());
            assertThat(string(conn, "SELECT announcement_id " + pub)).isEqualTo(announcement.toString());
            assertThat(string(conn, "SELECT user_id " + pub)).as("user_id unchanged (actorId)").isEqualTo(user.toString());
            assertThat(string(conn, "SELECT requested_storage_days " + pub)).isEqualTo("3");
            String unpub = "FROM delivery_needs WHERE id = '" + unpublished + "'";
            assertThat(string(conn, "SELECT parcel_id " + unpub)).isNull();
            assertThat(string(conn, "SELECT announcement_id " + unpub)).as("never published").isNull();

            assertThat(string(conn, "SELECT is_nullable FROM information_schema.columns WHERE table_name = "
                    + "'delivery_needs' AND column_name = 'status'")).isEqualTo("YES");
            insert(conn, "delivery_needs", cols("id", UUID.randomUUID(), "user_id", user,
                    "pickup_address_id", pickup, "delivery_address_id", drop, "title", "Nouveau", "status", null));

            long applied = count(conn, "SELECT count(*) FROM databasechangelog");
            Liquibase again = liquibase(conn);
            again.validate();
            again.update(new Contexts(), new LabelExpression("!gdd-final"));
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(applied);
        }
    }
}
