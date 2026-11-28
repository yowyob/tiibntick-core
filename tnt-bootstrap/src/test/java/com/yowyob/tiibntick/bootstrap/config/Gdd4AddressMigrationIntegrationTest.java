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

/** Lot L4: the Go-Freelancer addresses move to the core address book with the same ids. */
@Testcontainers
@Tag("integration")
class Gdd4AddressMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    @Test
    @DisplayName("addresses and their person links are copied with the same ids; replaying changes nothing")
    void addressesAndLinksAreMigrated() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);
            assertThat(count(conn, "SELECT count(*) FROM information_schema.tables WHERE table_name = 'tnt_addresses'"))
                    .as("previous version: no core address book").isZero();

            long seeded = count(conn, "SELECT count(*) FROM addresses");
            UUID full = UUID.randomUUID(), landmarkOnly = UUID.randomUUID(), badCoords = UUID.randomUUID(), half = UUID.randomUUID();
            insertAddress(conn, full, "Rue 1.234", "Face pharmacie", "Bastos", "Yaounde", 3.88, 11.51);
            insertAddress(conn, landmarkOnly, "", "Carrefour Warda", "", "Douala", null, null);
            insertAddress(conn, badCoords, "Rue folle", null, "X", "Yaounde", 999.0, 11.0);
            insertAddress(conn, half, "Rue demi", null, "X", "Yaounde", 3.0, null);
            UUID longText = UUID.randomUUID();
            String longStreet = "Rue " + "tres longue ".repeat(60), longLandmark = "Derriere " + "le grand marche ".repeat(40),
                    longDistrict = "Quartier " + "Nkolbisson ".repeat(40), longPostal = "BP " + "1234 ".repeat(30);
            insertAddress(conn, longText, longStreet, longLandmark, longDistrict, "Yaounde", null, null);
            exec(conn, "UPDATE addresses SET postal_code = '" + longPostal + "', type = 'SECONDARY' WHERE id = '" + longText + "'");
            UUID person = UUID.randomUUID();
            insert(conn, "persons", Map.of("id", person));
            UUID link = UUID.randomUUID();
            insert(conn, "person_addresses", Map.of("id", link, "person_id", person, "address_id", full));
            UUID secondaryLink = UUID.randomUUID();
            insert(conn, "person_addresses", Map.of("id", secondaryLink, "person_id", person, "address_id", longText));
            long legacy = count(conn, "SELECT count(*) FROM addresses");
            assertThat(legacy).as("seed data + 5").isEqualTo(seeded + 5);
            long legacyLinks = count(conn, "SELECT count(*) FROM person_addresses");

            // The copy (gdd-l4-410/411) is checked without gdd-l4-420 (D-134), which then deletes the links whose
            // owner is not an actor (this test's person is not one): see Gdd4OwnerActorMigrationIntegrationTest.
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-l4-orphans and !gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-l4-orphans and !gdd-final"))).isEmpty();

            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses")).as("control count").isEqualTo(legacy);
            assertThat(count(conn, "SELECT count(*) FROM addresses a JOIN tnt_geography.tnt_addresses t ON t.id = a.id"))
                    .as("same ids").isEqualTo(legacy);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses WHERE id = '" + full
                    + "' AND street = 'Rue 1.234' AND landmark = 'Face pharmacie' AND district = 'Bastos' AND latitude = 3.88"))
                    .isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses WHERE id = '" + landmarkOnly
                    + "' AND street IS NULL AND landmark = 'Carrefour Warda' AND district IS NULL"))
                    .as("blank text becomes absent").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses WHERE id IN ('" + badCoords + "','" + half
                    + "') AND latitude IS NULL AND longitude IS NULL")).as("unusable coordinates dropped").isEqualTo(2);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links WHERE id = '" + link
                    + "' AND owner_id = '" + person + "' AND address_id = '" + full + "' AND label = 'PRIMARY'"))
                    .as("link recreated, labelled with the address type (default PRIMARY)").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links WHERE id = '" + secondaryLink
                    + "' AND label = 'SECONDARY'")).as("label = addresses.type").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses WHERE id = '" + longText
                    + "' AND street = '" + longStreet.strip() + "' AND landmark = '" + longLandmark.strip()
                    + "' AND district = '" + longDistrict.strip() + "' AND postal_code = '" + longPostal.strip() + "'"))
                    .as("long legacy values (> 255 / > 50 characters) copied whole").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links")).as("every link").isEqualTo(legacyLinks);

            // replay of the data statements: nothing changes (idempotent)
            for (String file : new String[] {"20261124_gdd_l4_410_addresses_to_core.sql", "20261124_gdd_l4_411_address_links_to_core.sql"}) {
                exec(conn, java.nio.file.Files.readString(java.nio.file.Path.of(
                        "../coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/" + file)));
            }
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses")).isEqualTo(legacy);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links")).isEqualTo(legacyLinks);
        }
    }

    @Test
    @DisplayName("gdd-l4-415: no foreign key to the legacy addresses table remains; a delivery need (and every other "
            + "former referrer) is created with addresses that exist only in the core address book")
    void recordsReferToCoreAddresses() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);
            String referrersSql = "SELECT t.relname || '.' || a.attname FROM pg_constraint c "
                    + "JOIN pg_class t ON t.oid = c.conrelid JOIN pg_class rt ON rt.oid = c.confrelid "
                    + "JOIN pg_namespace rn ON rn.oid = rt.relnamespace "
                    + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1] "
                    + "WHERE c.contype = 'f' AND rt.relname = 'addresses' AND rn.nspname = 'public' ORDER BY 1";
            java.util.List<String> referrers = new java.util.ArrayList<>();
            try (java.sql.Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery(referrersSql)) {
                while (rs.next()) referrers.add(rs.getString(1));
            }
            assertThat(referrers).as("previous version: the legacy foreign keys")
                    .contains("delivery_needs.pickup_address_id", "delivery_needs.delivery_address_id",
                            "person_addresses.address_id");

            Liquibase upgrade = liquibase(conn);
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(count(conn, "SELECT count(*) FROM (" + referrersSql + ") r")).as("no FK to addresses left").isZero();

            UUID pickup = coreAddress(conn, "Yaounde"), drop = coreAddress(conn, "Douala");
            UUID need = UUID.randomUUID();
            insert(conn, "delivery_needs", Map.of("id", need, "pickup_address_id", pickup, "delivery_address_id", drop));
            assertThat(count(conn, "SELECT count(*) FROM delivery_needs WHERE id = '" + need + "' AND pickup_address_id = '"
                    + pickup + "' AND delivery_address_id = '" + drop + "'")).as("need created").isEqualTo(1);
            for (String referrer : referrers) {
                String[] tc = referrer.split("\\.");
                if (tc[0].equals("delivery_needs")) continue;
                if (count(conn, "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '"
                        + tc[0] + "'") == 0) continue;
                UUID address = coreAddress(conn, "Bafoussam");
                insert(conn, tc[0], Map.of(tc[1], address));
                assertThat(count(conn, "SELECT count(*) FROM " + tc[0] + " WHERE " + tc[1] + " = '" + address + "'"))
                        .as(referrer).isEqualTo(1);
            }
            assertThat(count(conn, "SELECT count(*) FROM addresses a JOIN tnt_geography.tnt_addresses t ON t.id = a.id "
                    + "WHERE t.city IN ('Yaounde', 'Douala', 'Bafoussam') AND t.street = 'core only'"))
                    .as("nothing written to the legacy table").isZero();
        }
    }

    private static UUID coreAddress(Connection conn, String city) throws Exception {
        UUID id = UUID.randomUUID();
        exec(conn, "INSERT INTO tnt_geography.tnt_addresses (id, street, city, country) VALUES ('" + id
                + "', 'core only', '" + city + "', 'CM')");
        return id;
    }

    private static void insertAddress(Connection conn, UUID id, String street, String desc, String district, String city,
                                      Double lat, Double lon) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("street", street); row.put("description", desc); row.put("district", district);
        row.put("city", city); row.put("country", "CM"); row.put("latitude", lat); row.put("longitude", lon);
        insert(conn, "addresses", row);
    }
}
