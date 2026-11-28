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

/** Lot L4: PAID rows of the legacy payments table become billing settlements of their delivery (gdd-l4-414). */
@Testcontainers
@Tag("integration")
class Gdd4PaymentMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = newPostgis();

    @Test
    @DisplayName("PAID rows stay paid: CASH -> CASH_COLLECTED, wallet -> WALLET_TRANSFER PAID, mobile money -> EXTERNAL_PROVIDER PAID; legacy paid_at kept; 5 % commission DUE; never a second settlement")
    void paidPaymentsBecomeSettlements() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
            conn.setAutoCommit(true);

            UUID tenant = UUID.randomUUID();
            UUID cashDelivery = coreDelivery(conn, tenant);
            UUID walletDelivery = coreDelivery(conn, tenant);
            UUID pendingDelivery = coreDelivery(conn, tenant);
            UUID settledDelivery = coreDelivery(conn, tenant);
            UUID kernelDelivery = coreDelivery(conn, tenant);
            UUID unknownDelivery = UUID.randomUUID();

            UUID cash = payment(conn, cashDelivery, 1000.0, "CASH", "PAID", null, "2026-03-04 10:00:00");
            UUID wallet = payment(conn, walletDelivery, 2000.0, "ORANGE_MONEY", "PAID", "TX-9", "2026-03-05 11:30:00");
            UUID kernel = payment(conn, kernelDelivery, 300.0, "kernel_wallet", "PAID", null, null);
            UUID pending = payment(conn, pendingDelivery, 500.0, "CASH", "PENDING", null, null);
            UUID orphan = payment(conn, unknownDelivery, 700.0, "CASH", "PAID", null, null);
            UUID already = payment(conn, settledDelivery, 900.0, "MOBILE_MONEY", "PAID", null, null);
            UUID existing = UUID.randomUUID();
            exec(conn, "INSERT INTO billing.wallet_settlements (id, tenant_id, reference_type, reference_id, amount, currency, "
                    + "method, status) VALUES ('" + existing + "', '" + tenant + "', 'DELIVERY', '" + settledDelivery
                    + "', 900, 'XAF', 'WALLET_TRANSFER', 'PAID')");
            long before = count(conn, "SELECT count(*) FROM billing.wallet_settlements");

            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            conn.setAutoCommit(true);
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements WHERE id = '" + cash + "' AND tenant_id = '"
                    + tenant + "' AND reference_type = 'DELIVERY' AND reference_id = '" + cashDelivery + "' AND amount = 1000"
                    + " AND currency = 'XAF' AND method = 'CASH' AND status = 'CASH_COLLECTED' AND commission_rate = 0.05"
                    + " AND commission_amount = 50 AND commission_status = 'DUE' AND paid_at = TIMESTAMPTZ '2026-03-04 10:00:00'"))
                    .as("cash payment").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements WHERE id = '" + wallet + "' AND reference_id = '"
                    + walletDelivery + "' AND method = 'EXTERNAL_PROVIDER' AND status = 'PAID' AND kernel_reference = 'TX-9'"
                    + " AND commission_amount = 100 AND commission_status = 'DUE' AND paid_at = TIMESTAMPTZ '2026-03-05 11:30:00'"))
                    .as("mobile money payment stays PAID").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements WHERE id = '" + kernel + "' AND method = "
                    + "'WALLET_TRANSFER' AND status = 'PAID' AND commission_amount = 15 AND commission_status = 'DUE'"
                    + " AND paid_at IS NOT NULL"))
                    .as("wallet payment without paid_at: PAID, paid at its creation").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements WHERE id IN ('" + pending + "', '" + orphan
                    + "', '" + already + "')")).as("not PAID, unknown delivery, already settled: not copied").isZero();
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements WHERE id = '" + existing
                    + "' AND status = 'PAID'")).as("existing settlement untouched").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements")).isEqualTo(before + 3);

            exec(conn, java.nio.file.Files.readString(java.nio.file.Path.of(
                    "../coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/"
                            + "20261125_gdd_l4_414_payments_to_settlements.sql")));
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_settlements")).as("replay").isEqualTo(before + 3);
        }
    }

    private static UUID coreDelivery(Connection conn, UUID tenant) throws Exception {
        UUID id = UUID.randomUUID();
        insert(conn, "tnt_deliveries", Map.of("id", id, "tenant_id", tenant));
        return id;
    }

    private static UUID payment(Connection conn, UUID delivery, double amount, String method, String status, String ref,
                                String paidAt) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("delivery_id", delivery); row.put("amount", amount); row.put("payment_method", method);
        row.put("status", status); row.put("transaction_reference", ref); row.put("currency", "XAF");
        if (paidAt != null) row.put("paid_at", java.sql.Timestamp.valueOf(paidAt));
        insert(conn, "payments", row);
        return id;
    }
}
