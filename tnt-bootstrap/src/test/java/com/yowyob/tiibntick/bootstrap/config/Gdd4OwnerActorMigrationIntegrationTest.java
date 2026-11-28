package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.core.geo.adapter.out.persistence.R2dbcAddressRepository;
import com.yowyob.tiibntick.core.geo.application.service.AddressService;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter.HubPackageEntryRepositoryAdapter;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository.HubPackageEntryR2dbcRepository;
import com.yowyob.tiibntick.core.inventory.application.service.HubInventoryApplicationService;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correction D-128 (gdd-l4-417): the owners of the address links (gdd-l4-411) and of the relay deposits (gdd-l4-412)
 * copied with a legacy Go-Freelancer id are converted to the core actor id, through the correspondences of lot L3
 * only. Rows without (or with an ambiguous) correspondence are left as they are and counted.
 * Then D-134 (gdd-l4-420): the address links left without a safe actor owner are deleted; the relay deposit entries
 * are never deleted (counted only).
 */
@Testcontainers
@Tag("integration")
class Gdd4OwnerActorMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    private static final String SQL_FILE = "../coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/"
            + "20261126_gdd_l4_417_legacy_owners_to_actor_ids.sql";

    @Test
    @DisplayName("legacy owners become actor ids; unknown/ambiguous/duplicate rows are left and counted; the owner reads "
            + "its migrated addresses and deposits through the real services; then the orphan address links are "
            + "deleted (D-134), deposit entries kept; replays change nothing")
    void legacyOwnersAreConvertedToActorIds() throws Exception {
        String url = createDatabase(POSTGRES);
        try (Connection conn = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);

            // 1. person -> users -> gofp_users (persons.user_id = core_user_id)
            UUID actor1 = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor1, "core_user_id", actor1));
            UUID person1 = UUID.randomUUID();
            insert(conn, "persons", Map.of("id", person1, "user_id", actor1));
            // 2. legacy id = gofp_clients.core_client_id
            UUID actor2 = UUID.randomUUID(), legacyClient2 = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor2, "core_user_id", actor2));
            insert(conn, "gofp_clients", Map.of("id", UUID.randomUUID(), "core_client_id", legacyClient2, "core_user_id", actor2));
            // 3. legacy id = gofp_freelancers.id = core_freelancer_id
            UUID actor3 = UUID.randomUUID(), freelancer3 = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor3, "core_user_id", actor3));
            insert(conn, "gofp_freelancers", Map.of("id", freelancer3, "core_freelancer_id", freelancer3, "core_user_id", actor3));
            // 4. legacy id = core client profile id
            UUID actor4 = UUID.randomUUID(), clientProfile4 = UUID.randomUUID();
            clientProfile(conn, clientProfile4, actor4);
            // 5. already an actor id
            UUID actor5 = UUID.randomUUID();
            clientProfile(conn, UUID.randomUUID(), actor5);
            // 6. no correspondence
            UUID unknown6 = UUID.randomUUID();
            // 7. ambiguous: one legacy id, two different actors
            UUID ambiguous7 = UUID.randomUUID(), actor7a = UUID.randomUUID(), actor7b = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor7a, "core_user_id", actor7a));
            insert(conn, "gofp_users", Map.of("id", actor7b, "core_user_id", actor7b));
            insert(conn, "gofp_clients", Map.of("id", UUID.randomUUID(), "core_client_id", ambiguous7, "core_user_id", actor7a));
            insert(conn, "gofp_freelancers", Map.of("id", UUID.randomUUID(), "core_freelancer_id", ambiguous7, "core_user_id", actor7b));
            // 8. the actor already holds the same link (same address, same label) under its actor id
            UUID actor8 = UUID.randomUUID(), person8 = UUID.randomUUID();
            insert(conn, "gofp_users", Map.of("id", actor8, "core_user_id", actor8));
            insert(conn, "persons", Map.of("id", person8, "user_id", actor8));

            UUID[] addr = new UUID[9];
            for (int i = 1; i <= 8; i++) addr[i] = address(conn, "Rue " + i);
            Map<String, UUID> links = new LinkedHashMap<>();
            links.put("person1", link(conn, person1, addr[1]));
            links.put("client2", link(conn, legacyClient2, addr[2]));
            links.put("freelancer3", link(conn, freelancer3, addr[3]));
            links.put("profile4", link(conn, clientProfile4, addr[4]));
            links.put("actor5", link(conn, actor5, addr[5]));
            links.put("unknown6", link(conn, unknown6, addr[6]));
            links.put("ambiguous7", link(conn, ambiguous7, addr[7]));
            links.put("person8", link(conn, person8, addr[8]));
            links.put("actor8", link(conn, actor8, addr[8]));

            UUID tenant = UUID.randomUUID(), hub = UUID.randomUUID();
            exec(conn, "INSERT INTO tnt_geography.road_nodes (id, tenant_id, type, latitude, longitude, name, city_code) "
                    + "VALUES ('n-" + hub.toString().substring(0, 30) + "', '" + tenant + "', 'RELAY_HUB', 3.8, 11.5, 'Relais', 'YDE')");
            exec(conn, "INSERT INTO tnt_geography.relay_hubs (id, tenant_id, node_id, capacity_slots, current_occupancy, status) "
                    + "VALUES ('" + hub + "', '" + tenant + "', 'n-" + hub.toString().substring(0, 30) + "', 10, 0, 'ACTIVE')");
            insert(conn, "logistics", Map.of("id", hub));
            UUID depPerson1 = deposit(conn, person1, hub, "DELIVERED");
            UUID depFreelancer3 = deposit(conn, freelancer3, hub, "RETRIEVED");
            UUID depUnknown6 = deposit(conn, unknown6, hub, "DELIVERED");

            // Upgrade to the published state (gdd-l4-410..416): the owners are still the legacy ids.
            Liquibase published = liquibase(conn);
            published.update(new Contexts(), new LabelExpression("!gdd-l4-owners and !gdd-final"));
            conn.setAutoCommit(true);
            assertThat(owner(conn, links.get("person1"))).as("before: legacy person id").isEqualTo(person1);
            assertThat(owner(conn, links.get("client2"))).isEqualTo(legacyClient2);
            assertThat(owner(conn, links.get("freelancer3"))).isEqualTo(freelancer3);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depPerson1
                    + "' AND owner_actor_id = '" + person1 + "'")).as("before: deposit owner = legacy id").isEqualTo(1);
            long linksBefore = count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links");
            long entriesBefore = count(conn, "SELECT count(*) FROM tnt_hub_package_entries");

            // Phase 1: gdd-l4-417 alone (gdd-l4-420, which deletes the orphans, is checked in phase 2).
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-l4-orphans and !gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-l4-orphans and !gdd-final"))).isEmpty();

            assertThat(owner(conn, links.get("person1"))).as("persons.user_id -> gofp_users.core_user_id").isEqualTo(actor1);
            assertThat(owner(conn, links.get("client2"))).as("gofp_clients.core_client_id -> core_user_id").isEqualTo(actor2);
            assertThat(owner(conn, links.get("freelancer3"))).as("gofp_freelancers.id -> core_user_id").isEqualTo(actor3);
            assertThat(owner(conn, links.get("profile4"))).as("client profile id -> actor_id").isEqualTo(actor4);
            assertThat(owner(conn, links.get("actor5"))).as("already an actor id").isEqualTo(actor5);
            assertThat(owner(conn, links.get("unknown6"))).as("no correspondence: left").isEqualTo(unknown6);
            assertThat(owner(conn, links.get("ambiguous7"))).as("ambiguous: left").isEqualTo(ambiguous7);
            assertThat(owner(conn, links.get("person8"))).as("would duplicate the actor's link: left").isEqualTo(person8);
            assertThat(owner(conn, links.get("actor8"))).isEqualTo(actor8);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links")).as("no row added or deleted")
                    .isEqualTo(linksBefore);

            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depPerson1
                    + "' AND owner_actor_id = '" + actor1 + "' AND picked_up_at IS NULL")).as("deposit owner converted").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depFreelancer3
                    + "' AND owner_actor_id = '" + actor3 + "' AND picked_up_by_actor_id = '" + actor3 + "'"))
                    .as("retrieved deposit: owner and picker converted").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depUnknown6
                    + "' AND owner_actor_id = '" + unknown6 + "'")).as("no correspondence: left").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries")).isEqualTo(entriesBefore);

            // The owner reads its migrated addresses and deposits through the real core services.
            ConnectionFactory cf = r2dbc(url);
            AddressService addresses = new AddressService(new R2dbcAddressRepository(DatabaseClient.create(cf)));
            List<AddressLink> actor1Links = addresses.findLinksByOwner(actor1).collectList().block();
            assertThat(actor1Links).extracting(AddressLink::addressId).containsExactly(addr[1]);
            assertThat(addresses.findLinksByOwner(actor2).collectList().block())
                    .extracting(AddressLink::addressId).containsExactly(addr[2]);
            assertThat(addresses.findLinksByOwner(person1).collectList().block()).as("legacy id no longer owns it").isEmpty();

            HubPackageEntryR2dbcRepository springData = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(cf))
                    .getRepository(HubPackageEntryR2dbcRepository.class);
            HubInventoryApplicationService inventory =
                    new HubInventoryApplicationService(new HubPackageEntryRepositoryAdapter(springData));
            assertThat(inventory.findEntriesOfOwner(actor1).collectList().block())
                    .extracting(HubPackageEntry::id).containsExactly(depPerson1);
            assertThat(inventory.findEntriesOfOwner(actor3).collectList().block())
                    .extracting(HubPackageEntry::id).containsExactly(depFreelancer3);

            // Replay: nothing changes.
            exec(conn, Files.readString(Path.of(SQL_FILE)));
            assertThat(owner(conn, links.get("person1"))).isEqualTo(actor1);
            assertThat(owner(conn, links.get("unknown6"))).isEqualTo(unknown6);
            assertThat(owner(conn, links.get("person8"))).isEqualTo(person8);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE owner_actor_id IN ('" + actor1
                    + "','" + actor3 + "')")).isEqualTo(2);

            // Phase 2: gdd-l4-420 (D-134) deletes the migrated links without a safe actor owner, keeps the rest,
            // never deletes an address nor a relay deposit entry.
            long addressesBefore = count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses");
            String migratedLinks = "SELECT count(*) FROM tnt_geography.tnt_address_links l JOIN person_addresses pa ON pa.id = l.id";
            long migratedBefore = count(conn, migratedLinks);
            long linksTotalBefore = count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links");
            Liquibase orphans = liquibase(conn);
            orphans.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(orphans.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            for (String gone : List.of("unknown6", "ambiguous7", "person8")) {
                assertThat(owner(conn, links.get(gone))).as("orphan link %s deleted", gone).isNull();
            }
            for (String kept : List.of("person1", "client2", "freelancer3", "profile4", "actor5", "actor8")) {
                assertThat(owner(conn, links.get(kept))).as("link %s kept", kept).isNotNull();
            }
            // kept: the 6 links of this scenario with a safe actor owner; deleted: the 3 orphans above and the seed
            // links of 20260219_seed_test_data.sql (seed persons are linked to no actor)
            assertThat(count(conn, migratedLinks)).as("only the migrated links with a safe actor owner remain").isEqualTo(6);
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_address_links"))
                    .as("only migrated links are deleted").isEqualTo(linksTotalBefore - (migratedBefore - 6));
            assertThat(count(conn, "SELECT count(*) FROM tnt_geography.tnt_addresses")).as("no address deleted")
                    .isEqualTo(addressesBefore);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries")).as("no deposit entry deleted")
                    .isEqualTo(entriesBefore);
            assertThat(count(conn, "SELECT count(*) FROM tnt_hub_package_entries WHERE id = '" + depUnknown6
                    + "' AND owner_actor_id = '" + unknown6 + "'")).as("orphan deposit entry kept as is").isEqualTo(1);
            assertThat(addresses.findLinksByOwner(actor8).collectList().block()).as("the actor's own link survives")
                    .extracting(AddressLink::addressId).containsExactly(addr[8]);
            assertThat(addresses.findLinksByOwner(actor1).collectList().block())
                    .extracting(AddressLink::addressId).containsExactly(addr[1]);

            // Replay of gdd-l4-420: nothing more is deleted.
            exec(conn, Files.readString(Path.of(SQL_FILE.replace("20261126_gdd_l4_417_legacy_owners_to_actor_ids.sql",
                    "20261126_gdd_l4_420_delete_orphan_address_links.sql"))));
            assertThat(count(conn, migratedLinks)).isEqualTo(6);
        }
    }

    private static ConnectionFactory r2dbc(String jdbcUrl) {
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        return ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":" + POSTGRES.getPassword()
                + "@" + uri.getHost() + ":" + uri.getPort() + uri.getPath());
    }

    private static void clientProfile(Connection conn, UUID id, UUID actorId) throws Exception {
        exec(conn, "INSERT INTO tnt_actor.client_profiles (id, tenant_id, actor_id, created_at, updated_at) VALUES ('"
                + id + "', '" + UUID.randomUUID() + "', '" + actorId + "', now(), now())");
    }

    private static UUID address(Connection conn, String street) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("street", street); row.put("city", "Yaounde"); row.put("country", "CM");
        insert(conn, "addresses", row);
        return id;
    }

    private static UUID link(Connection conn, UUID personId, UUID addressId) throws Exception {
        UUID id = UUID.randomUUID();
        insert(conn, "person_addresses", Map.of("id", id, "person_id", personId, "address_id", addressId));
        return id;
    }

    private static UUID deposit(Connection conn, UUID clientId, UUID hub, String status) throws Exception {
        UUID packet = UUID.randomUUID();
        insert(conn, "packets", Map.of("id", packet));
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("packet_id", packet); row.put("client_id", clientId); row.put("logistics_id", hub);
        row.put("storage_fee", 100.0); row.put("penalty_fee", 0.0); row.put("currency", "XAF"); row.put("status", status);
        if ("RETRIEVED".equals(status)) row.put("retrieved_at", "2026-11-01T10:00:00Z");
        insert(conn, "relay_deposits", row);
        return id;
    }

    private static UUID owner(Connection conn, UUID linkId) throws Exception {
        try (var s = conn.createStatement();
             var rs = s.executeQuery("SELECT owner_id FROM tnt_geography.tnt_address_links WHERE id = '" + linkId + "'")) {
            return rs.next() ? rs.getObject(1, UUID.class) : null;
        }
    }
}
