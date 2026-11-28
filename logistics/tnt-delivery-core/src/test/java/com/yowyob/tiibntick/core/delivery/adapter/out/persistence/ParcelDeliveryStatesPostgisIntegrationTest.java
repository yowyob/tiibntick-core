package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementResponseRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcDeliveryRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.application.service.RelayStorageService;
import com.yowyob.tiibntick.core.delivery.application.service.ReturnDeliveryService;
import com.yowyob.tiibntick.core.delivery.application.service.ReturnGuaranteeService;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelReturnCause;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RelayDeposit;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot evt-l2 on a real PostGIS with the real module changelog:
 * <ul>
 *   <li>upgrade of a database populated before the lot (changesets {@code evt-l2-001…011}): AT_RELAY_POINT renamed,
 *       CREATED with a courier becomes ASSIGNED, attempt counter −1, parcel code taken from its single delivery code
 *       (never chosen among several, D-136), parcel state taken from its latest delivery (spec §2.1);</li>
 *   <li>the HALT precondition on a database still holding TIMED_OUT / SLA_BREACHED;</li>
 *   <li>the application backfill of the parcels left without code; the unique index on the parcel code;</li>
 *   <li>a delivery driven through the real adapters up to a destination relay, the storage expiry (D12) by
 *       {@link RelayStorageService}, the parcel RETURN_REQUESTED and the RETURN draft, all read back from the
 *       database; lookup by the parcel code, by a legacy delivery code, and the history of the parcel.</li>
 * </ul>
 *
 * <p>When {@code TNT_IT_DB_PORT} is set, a running PostGIS is used ({@code TNT_IT_DB_HOST/NAME/USER/PASSWORD}) with
 * throw-away databases; otherwise a Testcontainers {@code postgis/postgis:18-3.6} is started.
 */
@Tag("integration")
class ParcelDeliveryStatesPostgisIntegrationTest {

    static final String MASTER = "db/changelog/tnt-delivery-master.yaml";
    static final int EVT_L2_CHANGESETS = 13;

    static PostgreSQLContainer<?> container;
    static String host;
    static int port;
    static String user;
    static String password;
    static String adminDatabase;
    static final List<String> created = new ArrayList<>();

    static final UUID TENANT = UUID.randomUUID();
    static final UUID P_RELAY = UUID.randomUUID();
    static final UUID P_MULTI = UUID.randomUUID();
    static final UUID P_ASSIGNED = UUID.randomUUID();
    static final UUID P_FAILED = UUID.randomUUID();
    static final UUID D_RELAY = UUID.randomUUID();
    static final UUID D_MULTI_OLD = UUID.randomUUID();
    static final UUID D_MULTI_NEW = UUID.randomUUID();
    static final UUID D_ASSIGNED = UUID.randomUUID();
    static final UUID D_FAILED = UUID.randomUUID();
    static final String RELAY_CODE = "TNT-20261101-AAAAAAAA";
    static final String MULTI_OLD_CODE = "TNT-20261101-BBBBBBBB";
    static final String MULTI_NEW_CODE = "TNT-20261102-CCCCCCCC";

    static String upgraded;

    @BeforeAll
    @SuppressWarnings("resource")
    static void start() throws Exception {
        String externalPort = System.getenv("TNT_IT_DB_PORT");
        if (externalPort != null && !externalPort.isBlank()) {
            host = env("TNT_IT_DB_HOST", "localhost");
            port = Integer.parseInt(externalPort.trim());
            user = env("TNT_IT_DB_USER", "tiibntick");
            password = env("TNT_IT_DB_PASSWORD", "tiibntick_pass");
            adminDatabase = env("TNT_IT_DB_NAME", "tiibntick_core");
        } else {
            container = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("tnt_evt_l2_it").withUsername("tnt_test").withPassword("tnt_test_secret");
            container.start();
            host = container.getHost();
            port = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
            user = container.getUsername();
            password = container.getPassword();
            adminDatabase = container.getDatabaseName();
        }
        upgraded = newDatabase();
        migrateAllBut(upgraded, EVT_L2_CHANGESETS);
        try (Connection c = jdbc(upgraded)) {
            seedLegacy(c);
        }
        migrate(upgraded);
    }

    @AfterAll
    static void stop() throws Exception {
        if (container != null) {
            container.stop();
            return;
        }
        try (Connection c = jdbc(adminDatabase); Statement st = c.createStatement()) {
            for (String db : created) {
                st.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
            }
        }
    }

    // ── migration of a populated database ─────────────────────────────────────────────────────

    @Test
    @DisplayName("upgrade: AT_RELAY_POINT -> AT_RELAY_HUB, CREATED+courier -> ASSIGNED, attempts -1, max_attempts 3")
    void deliveryRowsAreUpgraded() throws Exception {
        try (Connection c = jdbc(upgraded)) {
            assertThat(one(c, "SELECT status FROM tnt_deliveries WHERE id = '" + D_RELAY + "'")).isEqualTo("AT_RELAY_HUB");
            assertThat(one(c, "SELECT delivery_attempt_number FROM tnt_deliveries WHERE id = '" + D_RELAY + "'"))
                    .isEqualTo("1");
            assertThat(one(c, "SELECT status FROM tnt_deliveries WHERE id = '" + D_ASSIGNED + "'")).isEqualTo("ASSIGNED");
            assertThat(one(c, "SELECT delivery_attempt_number FROM tnt_deliveries WHERE id = '" + D_ASSIGNED + "'"))
                    .isEqualTo("0");
            assertThat(one(c, "SELECT COUNT(*) FROM tnt_deliveries WHERE max_attempts IS DISTINCT FROM 3")).isEqualTo("0");
            assertThat(one(c, "SELECT COUNT(*) FROM tnt_deliveries WHERE status = 'AT_RELAY_POINT'")).isEqualTo("0");
        }
    }

    @Test
    @DisplayName("upgrade: a parcel takes its single delivery code, never one of several; its state comes from its delivery")
    void parcelRowsAreUpgraded() throws Exception {
        try (Connection c = jdbc(upgraded)) {
            assertThat(one(c, "SELECT tracking_code FROM tnt_parcels WHERE id = '" + P_RELAY + "'")).isEqualTo(RELAY_CODE);
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + P_RELAY + "'")).isEqualTo("AT_RELAY_HUB");
            assertThat(one(c, "SELECT custody_holder_type FROM tnt_parcels WHERE id = '" + P_RELAY + "'")).isEqualTo("RELAY");
            assertThat(one(c, "SELECT current_delivery_id FROM tnt_parcels WHERE id = '" + P_RELAY + "'"))
                    .isEqualTo(D_RELAY.toString());
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + P_ASSIGNED + "'")).isEqualTo("ASSIGNED");
            // collected then failed: waits for its return (P11); no delivery is current any more
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + P_FAILED + "'")).isEqualTo("RETURN_REQUESTED");
            assertThat(one(c, "SELECT current_delivery_id FROM tnt_parcels WHERE id = '" + P_FAILED + "'")).isNull();
            // two distinct delivery codes: none is chosen by the migration (left to the application backfill)
            assertThat(one(c, "SELECT tracking_code FROM tnt_parcels WHERE id = '" + P_MULTI + "'")).isNull();
            assertThat(one(c, "SELECT delivery_ids FROM tnt_parcels WHERE id = '" + P_MULTI + "'"))
                    .contains(D_MULTI_OLD.toString()).contains(D_MULTI_NEW.toString());
        }
    }

    @Test
    @DisplayName("the migration halts on a database still holding TIMED_OUT / SLA_BREACHED rows")
    void haltsOnRemovedStates() throws Exception {
        String db = newDatabase();
        migrateAllBut(db, EVT_L2_CHANGESETS);
        try (Connection c = jdbc(db)) {
            parcel(c, P_RELAY);
            delivery(c, UUID.randomUUID(), P_RELAY, "SLA_BREACHED", null, null, 0, null, Instant.now());
        }
        assertThatThrownBy(() -> migrate(db)).hasStackTraceContaining("TIMED_OUT");
        try (Connection c = jdbc(db)) {
            assertThat(one(c, "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE 'evt-l2-0%' AND id > 'evt-l2-005'"))
                    .isEqualTo("0");
        }
    }

    @Test
    @DisplayName("backfill: every parcel without code gets a fresh one; the old delivery codes still resolve; codes are unique")
    void backfillAndUniqueness() throws Exception {
        String db = newDatabase();
        migrateAllBut(db, EVT_L2_CHANGESETS);
        try (Connection c = jdbc(db)) {
            seedLegacy(c);
        }
        migrate(db);
        Adapters a = adapters(db);

        assertThat(a.parcels.countParcelsWithSeveralLegacyCodes().block()).isEqualTo(1L);
        Long done = new ParcelTrackingCodeBackfill(a.parcels, a.writer, true).run().block();
        assertThat(done).isEqualTo(1L);
        try (Connection c = jdbc(db)) {
            assertThat(one(c, "SELECT COUNT(*) FROM tnt_parcels WHERE tracking_code IS NULL")).isEqualTo("0");
            String fresh = one(c, "SELECT tracking_code FROM tnt_parcels WHERE id = '" + P_MULTI + "'");
            assertThat(fresh).matches("TNT-\\d{8}-[A-Z0-9]{8}").isNotIn(MULTI_OLD_CODE, MULTI_NEW_CODE);
            assertThatThrownBy(() -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("UPDATE tnt_parcels SET tracking_code = '" + RELAY_CODE + "' WHERE id = '"
                            + P_ASSIGNED + "'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("uq_tnt_parcels_tracking_code");
        }
        // a second run finds nothing to do
        assertThat(new ParcelTrackingCodeBackfill(a.parcels, a.writer, true).run().block()).isZero();

        // both old codes of the multi-code parcel resolve to its latest delivery, the history lists both legs
        Delivery byOld = a.deliveries.findByTrackingCode(MULTI_OLD_CODE).block();
        assertThat(byOld).isNotNull();
        assertThat(byOld.getParcel().getId()).isEqualTo(P_MULTI);
        assertThat(a.deliveries.findHistoryByTrackingCode(MULTI_NEW_CODE).map(Delivery::getId).collectList().block())
                .containsExactly(D_MULTI_OLD, D_MULTI_NEW);
        // a migrated relay row resolves by the parcel code and keeps its state
        Delivery relay = a.deliveries.findByTrackingCode(RELAY_CODE).block();
        assertThat(relay.getId()).isEqualTo(D_RELAY);
        assertThat(relay.getStatus()).isEqualTo(DeliveryStatus.AT_RELAY_HUB);
        assertThat(relay.getParcel().getStatus()).isEqualTo(ParcelStatus.AT_RELAY_HUB);
    }

    // ── a delivery through the real adapters ──────────────────────────────────────────────────

    @Test
    @DisplayName("delivery to a destination relay, storage expiry (D12): parcel RETURN_REQUESTED and RETURN draft, all persisted")
    void relayStorageExpiryEndToEnd() throws Exception {
        String db = newDatabase();
        migrate(db);
        Adapters a = adapters(db);
        List<DeliveryDomainEvent> published = new CopyOnWriteArrayList<>();
        DeliveryEventPublisher events = new DeliveryEventPublisher() {
            @Override public Mono<Void> publish(DeliveryDomainEvent event) { published.add(event); return Mono.empty(); }
            @Override public Mono<Void> publishAll(List<DeliveryDomainEvent> list) {
                published.addAll(list);
                return Mono.empty();
            }
        };
        DeliveryThresholdsPort thresholds = mock(DeliveryThresholdsPort.class);
        when(thresholds.forAgency(any())).thenReturn(Mono.just(new DeliveryThresholdsPort.Thresholds(3, 3, 1)));
        DeliveryTransitionExecutor executor = new DeliveryTransitionExecutor(a.deliveries, a.announcements, events,
                new ReturnDeliveryService(a.announcements, thresholds));
        // D-147, D-151: no wallet operation connected (lot L5) -> cases recorded NOT_HANDLED, never blocking
        executor.setReturnGuarantee(new ReturnGuaranteeService(
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort.class),
                a.announcements));

        UUID sender = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        Parcel parcel = Parcel.create(new PackageSpecification(2, 20, 20, 20, false, false, "Box"), TENANT, sender,
                TntEventActor.of(TntEventActor.Type.SENDER, sender));
        Delivery delivery = Delivery.create(TENANT, null, sender, parcel, address("Mokolo"), address("Bastos"),
                new RecipientInfo("Awa Ndiaye", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        delivery.setDestinationHub(hub);
        delivery.assignDeliveryPerson(courier, null, 3.0, null);
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
        Instant depositedAt = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        delivery.depositAtRelayHub(new RelayDeposit(hub, null, depositedAt, true, 3, 3,
                depositedAt.plus(3, ChronoUnit.DAYS), depositedAt.plus(2, ChronoUnit.DAYS)));
        executor.saveAndPublish(delivery).block();

        String code = parcel.getTrackingCode();
        try (Connection c = jdbc(db)) {
            assertThat(one(c, "SELECT tracking_code FROM tnt_deliveries WHERE id = '" + delivery.getId() + "'")).isNull();
            assertThat(one(c, "SELECT tracking_code FROM tnt_parcels WHERE id = '" + parcel.getId() + "'")).isEqualTo(code);
            assertThat(one(c, "SELECT status FROM tnt_parcels WHERE id = '" + parcel.getId() + "'")).isEqualTo("AT_RELAY_HUB");
            assertThat(one(c, "SELECT custody_hub_id FROM tnt_parcels WHERE id = '" + parcel.getId() + "'"))
                    .isEqualTo(hub.toString());
        }
        assertThat(published).filteredOn(AggregateEvent.class::isInstance).map(e -> ((AggregateEvent) e).eventType())
                .contains("DeliveryCreated", "DeliveryAssigned", "DeliveryPickedUp", "DeliveryInTransit",
                        "DeliveryAtRelayHub", "ParcelCreated", "ParcelAssigned", "ParcelPickedUp", "ParcelInTransit",
                        "ParcelAtRelayHub");
        assertThat(a.deliveries.findAtRelayHub(hub).map(Delivery::getId).collectList().block())
                .containsExactly(delivery.getId());
        assertThat(a.deliveries.findByTrackingCode(code).block().getId()).isEqualTo(delivery.getId());
        published.clear();

        RelayStorageService storage = new RelayStorageService(a.deliveries, executor);
        assertThat(storage.sendDueReminders(Instant.now()).block()).isEqualTo(1L);
        assertThat(storage.sendDueReminders(Instant.now()).block()).as("reminder sent once").isZero();
        assertThat(storage.expireStorage(Instant.now()).block()).isEqualTo(1L);
        assertThat(storage.expireStorage(Instant.now()).block()).as("nothing left to expire").isZero();

        Delivery failed = a.deliveries.findByIdNoTenant(delivery.getId()).block();
        assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo(DeliveryFailureReason.NOT_COLLECTED_AT_RELAY);
        assertThat(failed.getParcel().getStatus()).isEqualTo(ParcelStatus.RETURN_REQUESTED);
        assertThat(failed.getParcel().getReturnCause()).isEqualTo(ParcelReturnCause.NOT_COLLECTED_AT_RELAY);
        assertThat(failed.getParcel().getCurrentDeliveryId()).isNull();
        assertThat(published).filteredOn(AggregateEvent.class::isInstance).map(e -> ((AggregateEvent) e).eventType())
                .contains("DeliveryFailed", "ParcelReturnRequested", "AnnouncementDraft");

        List<DeliveryAnnouncement> drafts = a.announcementRows.findAll()
                .flatMap(e -> a.announcements.findById(e.getTenantId(), e.getId())).collectList().block();
        assertThat(drafts).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(AnnouncementKind.RETURN);
            assertThat(d.getStatus()).isEqualTo(AnnouncementStatus.DRAFT);
            assertThat(d.getParcel().getId()).isEqualTo(parcel.getId());
            // D-147, D-151: the sender pays the return of a parcel not collected, never the relay;
            // no announcement: sender contact unknown, no recipient
            assertThat(d.getClientId()).isEqualTo(sender);
            assertThat(d.getCreatorRole()).isEqualTo(com.yowyob.tiibntick.core.delivery.domain.model.enums
                    .AnnouncementCreatorRole.CLIENT);
            assertThat(d.getRecipient()).isNull();
            assertThatThrownBy(d::publish).hasMessageContaining("Recipient");
        });

        // the guarantee case is kept for the administrator (admin spec §5.2.1): never priced -> none frozen
        try (Connection c = jdbc(db)) {
            assertThat(one(c, "SELECT return_guarantee_status FROM tnt_deliveries WHERE id = '" + delivery.getId()
                    + "'")).isEqualTo("NOT_HANDLED");
            assertThat(one(c, "SELECT return_guarantee_reason FROM tnt_deliveries WHERE id = '" + delivery.getId()
                    + "'")).isEqualTo(ReturnGuaranteeService.NO_GUARANTEE_HELD);
        }
        assertThat(failed.getReturnGuaranteeStatus())
                .isEqualTo(com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus.NOT_HANDLED);

        // the public history of the parcel: one leg, failed
        assertThat(a.deliveries.findHistoryByTrackingCode(code).map(Delivery::getStatus).collectList().block())
                .containsExactly(DeliveryStatus.FAILED);
    }

    // ── lot L3: delivery alerts on the real schema ─────────────────────────────────────────────

    @Test
    @DisplayName("lot L3: the watch raises ETA_EXCEEDED and GPS_SIGNAL_LOST once, persists them, a position clears GPS")
    void alertsWatchEndToEnd() throws Exception {
        String db = newDatabase();
        migrate(db);
        Adapters a = adapters(db);
        List<DeliveryDomainEvent> published = new CopyOnWriteArrayList<>();
        DeliveryEventPublisher events = new DeliveryEventPublisher() {
            @Override public Mono<Void> publish(DeliveryDomainEvent event) { published.add(event); return Mono.empty(); }
            @Override public Mono<Void> publishAll(List<DeliveryDomainEvent> list) {
                published.addAll(list);
                return Mono.empty();
            }
        };
        DeliveryThresholdsPort thresholds = mock(DeliveryThresholdsPort.class);
        when(thresholds.forAgency(any())).thenReturn(Mono.just(new DeliveryThresholdsPort.Thresholds(3, 3, 1)));
        DeliveryTransitionExecutor executor = new DeliveryTransitionExecutor(a.deliveries, a.announcements, events,
                new ReturnDeliveryService(a.announcements, thresholds));
        executor.setReturnGuarantee(new ReturnGuaranteeService(
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort.class),
                a.announcements));

        UUID sender = UUID.randomUUID();
        Parcel parcel = Parcel.create(new PackageSpecification(2, 20, 20, 20, false, false, "Box"), TENANT, sender,
                TntEventActor.of(TntEventActor.Type.SENDER, sender));
        Delivery delivery = Delivery.create(TENANT, null, sender, parcel, address("Mokolo"), address("Bastos"),
                new RecipientInfo("Awa Ndiaye", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        delivery.assignDeliveryPerson(UUID.randomUUID(), null, 3.0, null);
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().minus(1, ChronoUnit.HOURS), 3.0, 15));
        delivery.recordPosition(Instant.now().minus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS));
        executor.saveAndPublish(delivery).block();
        Parcel delivered = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"), TENANT, sender,
                TntEventActor.of(TntEventActor.Type.SENDER, sender));
        Delivery cancelled = Delivery.create(TENANT, null, sender, delivered, address("Mokolo"), address("Bastos"),
                new RecipientInfo("Awa Ndiaye", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        cancelled.cancel("x");
        executor.saveAndPublish(cancelled).block();
        published.clear();

        assertThat(a.deliveries.findWatchCandidates(100).map(Delivery::getId).collectList().block())
                .contains(delivery.getId()).doesNotContain(cancelled.getId());

        com.yowyob.tiibntick.core.delivery.application.service.watch.DeliveryWatchService watch =
                new com.yowyob.tiibntick.core.delivery.application.service.watch.DeliveryWatchService(a.deliveries,
                        executor, new com.yowyob.tiibntick.core.delivery.config.DeliveryAlertProperties(), thresholds);
        assertThat(watch.scan(Instant.now()).block()).isEqualTo(1L);
        assertThat(watch.scan(Instant.now()).block()).as("raised once").isZero();

        Delivery reloaded = a.deliveries.findByIdNoTenant(delivery.getId()).block();
        assertThat(reloaded.activeAlertList()).containsExactly("ETA_EXCEEDED", "GPS_SIGNAL_LOST");
        assertThat(reloaded.isLate()).isTrue();
        assertThat(reloaded.getLastPositionAt()).isNotNull();
        try (Connection c = jdbc(db)) {
            assertThat(one(c, "SELECT active_alerts FROM tnt_deliveries WHERE id = '" + delivery.getId() + "'"))
                    .isEqualTo("ETA_EXCEEDED,GPS_SIGNAL_LOST");
        }
        assertThat(published).filteredOn(AggregateEvent.class::isInstance).map(e -> ((AggregateEvent) e).eventType())
                .containsExactly("DeliveryAlertRaised", "DeliveryAlertRaised");

        watch.onPosition(TENANT, delivery.getId(), Instant.now(), false, false).block();
        Delivery back = a.deliveries.findByIdNoTenant(delivery.getId()).block();
        assertThat(back.activeAlertList()).containsExactly("ETA_EXCEEDED");
        assertThat(published).filteredOn(AggregateEvent.class::isInstance).map(e -> ((AggregateEvent) e).eventType())
                .endsWith("DeliveryAlertCleared");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    record Adapters(R2dbcParcelRepository parcels, ParcelWriter writer, DeliveryRepositoryAdapter deliveries,
                    DeliveryAnnouncementRepositoryAdapter announcements, R2dbcAnnouncementRepository announcementRows) {
    }

    static Adapters adapters(String db) {
        R2dbcRepositoryFactory repos = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder().host(host).port(port).database(db)
                        .username(user).password(password).build())));
        R2dbcParcelRepository parcels = repos.getRepository(R2dbcParcelRepository.class);
        ParcelWriter writer = new ParcelWriter(parcels);
        R2dbcAnnouncementRepository announcementRows = repos.getRepository(R2dbcAnnouncementRepository.class);
        return new Adapters(parcels, writer,
                new DeliveryRepositoryAdapter(repos.getRepository(R2dbcDeliveryRepository.class), parcels, writer),
                new DeliveryAnnouncementRepositoryAdapter(announcementRows,
                        repos.getRepository(R2dbcAnnouncementResponseRepository.class), parcels, writer),
                announcementRows);
    }

    static DeliveryAddress address(String district) {
        return new DeliveryAddress("Rue 1", null, district, "Yaounde", "CM", new GeoCoordinates(3.87, 11.51));
    }

    static void seedLegacy(Connection c) throws Exception {
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        parcel(c, P_RELAY);
        parcel(c, P_MULTI);
        parcel(c, P_ASSIGNED);
        parcel(c, P_FAILED);
        UUID courier = UUID.randomUUID();
        delivery(c, D_RELAY, P_RELAY, "AT_RELAY_POINT", RELAY_CODE, courier, 2, t, t);
        delivery(c, D_MULTI_OLD, P_MULTI, "CANCELLED", MULTI_OLD_CODE, null, 0, null, t);
        delivery(c, D_MULTI_NEW, P_MULTI, "IN_TRANSIT", MULTI_NEW_CODE, courier, 1, t, t.plusSeconds(3600));
        delivery(c, D_ASSIGNED, P_ASSIGNED, "CREATED", "TNT-20261101-DDDDDDDD", courier, 0, null, t);
        delivery(c, D_FAILED, P_FAILED, "FAILED", "TNT-20261101-EEEEEEEE", courier, 3, t, t);
    }

    static void parcel(Connection c, UUID id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tnt_parcels (id, weight_kg, width_cm, height_cm, length_cm, created_at, updated_at)
                VALUES (?, 1, 10, 10, 10, now(), now())""")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    static void delivery(Connection c, UUID id, UUID parcel, String status, String code, UUID courier, int attempts,
                         Instant pickedUpAt, Instant createdAt) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tnt_deliveries (id, tenant_id, parcel_id, sender_id, status, urgency, pickup_city,
                                            delivery_city, recipient_name, recipient_phone, tracking_code,
                                            delivery_person_id, delivery_attempt_number, actual_pickup_time,
                                            created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'STANDARD', 'Douala', 'Douala', 'R', '+237600000000', ?, ?, ?, ?, ?, ?)""")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, parcel);
            ps.setObject(4, UUID.randomUUID());
            ps.setString(5, status);
            ps.setString(6, code);
            ps.setObject(7, courier);
            ps.setInt(8, attempts);
            ps.setObject(9, pickedUpAt == null ? null : java.sql.Timestamp.from(pickedUpAt));
            ps.setObject(10, java.sql.Timestamp.from(createdAt));
            ps.setObject(11, java.sql.Timestamp.from(createdAt));
            ps.executeUpdate();
        }
    }

    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    static String newDatabase() throws SQLException {
        String db = "tnt_evt_l2_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = jdbc(adminDatabase); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        created.add(db);
        return db;
    }

    static Connection jdbc(String db) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + db, user, password);
    }

    static void migrate(String db) throws Exception {
        try (Connection conn = jdbc(db)) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database).update(new Contexts(), new LabelExpression());
        }
    }

    static void migrateAllBut(String db, int n) throws Exception {
        try (Connection conn = jdbc(db)) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibase = new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
            int pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression()).size();
            liquibase.update(pending - n, new Contexts(), new LabelExpression());
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
