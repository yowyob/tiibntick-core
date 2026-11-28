package com.yowyob.tiibntick.core.inventory.adapter.out.geo;

import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter.HubPackageEntryRepositoryAdapter;
import com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository.HubPackageEntryR2dbcRepository;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-129: the parcels a revoked relay hub still holds are read from the real inventory table (PostGIS). */
@Testcontainers
@Tag("integration")
class HubCustodyAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tnt_inventory_custody").withUsername("tnt_test").withPassword("tnt_test_secret");

    @Test
    @DisplayName("only the entries of the hub not picked up are in custody, with their owner actor")
    void parcelsInCustody() throws Exception {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-inventory-master.yaml", new ClassLoaderResourceAccessor(), db)
                    .update(new Contexts(), new LabelExpression());
        }
        ConnectionFactory cf = ConnectionFactories.get("r2dbc:postgresql://tnt_test:tnt_test_secret@" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/tnt_inventory_custody");
        HubPackageEntryRepositoryAdapter entries = new HubPackageEntryRepositoryAdapter(
                new R2dbcRepositoryFactory(new R2dbcEntityTemplate(cf)).getRepository(HubPackageEntryR2dbcRepository.class));
        UUID tenant = UUID.randomUUID(), hub = UUID.randomUUID(), otherHub = UUID.randomUUID(), owner = UUID.randomUUID();

        HubPackageEntry held = entries.save(HubPackageEntry.deposit(tenant, hub, UUID.randomUUID(), "TRK-HELD", "A1",
                UUID.randomUUID(), null, owner, BigDecimal.ZERO, "XAF")).block();
        entries.save(HubPackageEntry.deposit(tenant, hub, UUID.randomUUID(), "TRK-GONE", "A2", UUID.randomUUID(), null)
                .markPickedUp(UUID.randomUUID())).block();
        entries.save(HubPackageEntry.deposit(tenant, otherHub, UUID.randomUUID(), "TRK-OTHER", "B1", UUID.randomUUID(), null))
                .block();

        List<HubCustodyItem> custody = new HubCustodyAdapter(entries).parcelsInCustody(hub).collectList().block();

        assertThat(custody).singleElement().satisfies(item -> {
            assertThat(item.entryId()).isEqualTo(held.id());
            assertThat(item.packageId()).isEqualTo(held.packageId());
            assertThat(item.trackingCode()).isEqualTo("TRK-HELD");
            assertThat(item.ownerActorId()).isEqualTo(owner);
            assertThat(item.depositedAt()).isNotNull();
        });
        assertThat(new HubCustodyAdapter(entries).parcelsInCustody(UUID.randomUUID()).collectList().block()).isEmpty();
    }
}
