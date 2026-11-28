package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketOrderEntity;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import liquibase.Contexts;
import liquibase.LabelExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.createDatabase;
import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.exec;
import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.liquibase;
import static com.yowyob.tiibntick.bootstrap.config.Gdd4MigrationSupport.newPostgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * Changesets {@code 008-align-*} of tnt-market-back-core: the market entities mapped 82 columns (and five {@code @Version} columns) their
 * changelogs never created, so no market order, quote, offer, review or contract could be written or read (found by the lot L8 wiring
 * test: "column market_orders.mission_id does not exist"). On a database as the previous version left it, with a
 * legacy order row, the upgrade adds every mapped column, carries the delivery and payment reference over, and the
 * order is then found by its delivery through R2DBC as the market consumer reads it.
 */
@Testcontainers
@Tag("integration")
class MarketSchemaAlignmentIntegrationTest {

    private static final String LABEL = "market-schema-008";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    @Test
    @DisplayName("upgrade: every column mapped by a market entity exists, the legacy order is found by its delivery")
    void marketEntitiesMatchTheirTables() throws Exception {
        String url = createDatabase(POSTGRES);
        UUID order = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        String tenant = UUID.randomUUID().toString();
        try (Connection conn = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            liquibase(conn).update(new Contexts(), new LabelExpression("!" + LABEL));
            exec(conn, "INSERT INTO tnt_market.market_orders (id, tenant_id, client_id, provider_id, status, "
                    + "delivery_mission_id, transaction_id, created_at, updated_at) VALUES ('" + order + "', '" + tenant
                    + "', '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'DISPATCHED', '" + delivery
                    + "', 'MOMO-42', now(), now())");

            liquibase(conn).update(new Contexts(), new LabelExpression());
            liquibase(conn).update(new Contexts(), new LabelExpression()); // idempotent re-run

            List<String> missing = new ArrayList<>();
            for (Class<?> entity : marketEntities()) {
                Table table = entity.getAnnotation(Table.class);
                String name = table.value().isEmpty() ? table.name() : table.value();
                Set<String> columns = columns(conn, table.schema(), name);
                for (Field field : entity.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            || field.isAnnotationPresent(org.springframework.data.annotation.Transient.class)) {
                        continue;
                    }
                    // explicit @Column, else the field name in snake case (@Id id, @Version version…)
                    Column column = field.getAnnotation(Column.class);
                    String col = column != null ? column.value()
                            : field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
                    if (!columns.contains(col)) {
                        missing.add(name + "." + col);
                    }
                }
            }
            assertThat(missing).isEmpty();
        }

        URI jdbc = URI.create(url.substring("jdbc:".length()));
        ConnectionFactory cf = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, jdbc.getHost())
                .option(ConnectionFactoryOptions.PORT, jdbc.getPort())
                .option(ConnectionFactoryOptions.DATABASE, jdbc.getPath().substring(1))
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build());
        List<MarketOrderEntity> found = new R2dbcEntityTemplate(cf)
                .select(query(where("mission_id").is(delivery).and("tenant_id").is(tenant)), MarketOrderEntity.class)
                .collectList().block(Duration.ofSeconds(20));
        assertThat(found).singleElement().satisfies(o -> {
            assertThat(o.getId()).isEqualTo(order);
            assertThat(o.getMissionId()).isEqualTo(delivery);
            assertThat(o.getTransactionRef()).isEqualTo("MOMO-42");
            assertThat(o.getQuantity()).isEqualTo(1);
            assertThat(o.isFragile()).isFalse();
            assertThat(o.getVersion()).isEqualTo(1L);
        });

        // write path: a new order with every block of the entity, then a versioned status update
        R2dbcEntityTemplate template = new R2dbcEntityTemplate(cf);
        UUID otherDelivery = UUID.randomUUID();
        MarketOrderEntity created = template.insert(MarketOrderEntity.builder().id(UUID.randomUUID()).isNew(true)
                .tenantId(tenant).clientId(UUID.randomUUID()).providerId(UUID.randomUUID()).status("DISPATCHED")
                .missionId(otherDelivery).baseAmount(2000L).distanceFee(300L).totalAmount(2300L).currency("XAF")
                .transactionRef("MOMO-43").pickupCity("Yaoundé").pickupLat(3.86).deliveryCity("Douala")
                .weightKg(2.5).fragile(true).quantity(2).specialInstructions("fragile")
                .createdAt(java.time.LocalDateTime.now()).updatedAt(java.time.LocalDateTime.now()).build())
                .block(Duration.ofSeconds(20));
        created.setStatus("IN_TRANSIT");
        created.setNew(false);
        template.update(created).block(Duration.ofSeconds(20));
        MarketOrderEntity reread = template.selectOne(query(where("mission_id").is(otherDelivery)),
                MarketOrderEntity.class).block(Duration.ofSeconds(20));
        assertThat(reread.getStatus()).isEqualTo("IN_TRANSIT");
        assertThat(reread.getDistanceFee()).isEqualTo(300L);
        assertThat(reread.getPickupCity()).isEqualTo("Yaoundé");
        assertThat(reread.isFragile()).isTrue();
        assertThat(reread.getVersion()).isEqualTo(created.getVersion());
        assertThat(reread.getVersion()).isGreaterThan(1L);
    }

    private static List<Class<?>> marketEntities() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Table.class));
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.yowyob.tiibntick.core.marketback")) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        assertThat(out).hasSizeGreaterThanOrEqualTo(7);
        return out;
    }

    private static Set<String> columns(Connection conn, String schema, String table) throws Exception {
        Set<String> out = new HashSet<>();
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = '" + schema
                        + "' AND table_name = '" + table + "'")) {
            while (rs.next()) out.add(rs.getString(1));
        }
        assertThat(out).as("table %s.%s", schema, table).isNotEmpty();
        return out;
    }
}
