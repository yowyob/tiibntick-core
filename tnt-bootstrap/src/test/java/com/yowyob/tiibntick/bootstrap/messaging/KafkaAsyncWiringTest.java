package com.yowyob.tiibntick.bootstrap.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.SubscriptionAttemptEvent;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EventPublisher;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification.NotificationStreamAdapter;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryEventNotifier;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.domain.service.MissionTrackingCodeCache;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.PermissionCache;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof, on a REAL broker and a REAL database, that the asynchronous / Kafka wiring of the
 * assembled application works: a message produced by the real producer (outbox publisher or
 * {@code KafkaTemplate}) is consumed by the real {@code @KafkaListener} and has its effect.
 *
 * <p>The test boots the complete {@code tnt-bootstrap} context (all modules). It needs live
 * infrastructure which is NOT started by the build (Testcontainers is unusable offline):
 * it is skipped unless the environment variable {@code TNT_IT_KAFKA_BOOTSTRAP} is set.
 *
 * <pre>
 * docker run -d --rm --name tnt-kafka-it -p 19092:9092 ... apache/kafka:3.8.0   # KRaft, advertised localhost:19092
 * docker run -d --rm --name tnt-pg-it -p 15433:5432 -e POSTGRES_USER=tiibntick -e POSTGRES_PASSWORD=... postgis/postgis:18-3.6
 * docker run -d --rm --name tnt-redis-it -p 16379:6379 redis:7-alpine
 * TNT_IT_KAFKA_BOOTSTRAP=localhost:19092 TNT_IT_DB_PORT=15433 TNT_IT_DB_PASSWORD=... TNT_IT_REDIS_PORT=16379 \
 *   mvn -o test -pl tnt-bootstrap -am -Dtest=KafkaAsyncWiringTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Optional variables: {@code TNT_IT_DB_HOST} (localhost), {@code TNT_IT_DB_NAME} (tiibntick_core),
 * {@code TNT_IT_DB_USER} (tiibntick), {@code TNT_IT_REDIS_HOST} (localhost).
 *
 * <p>Terminal effects that need an external system (Kernel notification gateway, incident
 * persistence...) are observed on a mock of the port the consumer calls; everything between the
 * producer and that port is real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@DisplayName("Kafka / async wiring — real broker, real context")
class KafkaAsyncWiringTest {

    private static final String SYSTEM_TENANT = "d3b07384-d9a0-4c9c-b7d1-5f2a1b6c0e91";
    private static final UUID PLATFORM_BOOK = UUID.randomUUID();

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BOOTSTRAP", () -> System.getenv("TNT_IT_KAFKA_BOOTSTRAP"));
        registry.add("DB_HOST", () -> env("TNT_IT_DB_HOST", "localhost"));
        registry.add("DB_PORT", () -> env("TNT_IT_DB_PORT", "5433"));
        registry.add("DB_NAME", () -> env("TNT_IT_DB_NAME", "tiibntick_core"));
        registry.add("DB_USER", () -> env("TNT_IT_DB_USER", "tiibntick"));
        registry.add("DB_PASSWORD", () -> env("TNT_IT_DB_PASSWORD", "tiibntick_pass"));
        registry.add("REDIS_HOST", () -> env("TNT_IT_REDIS_HOST", "localhost"));
        registry.add("REDIS_PORT", () -> env("TNT_IT_REDIS_PORT", "6379"));
        registry.add("TNT_SYSTEM_TENANT_ID", () -> SYSTEM_TENANT);
        registry.add("TNT_ACCOUNTING_PLATFORM_ORGANIZATION_ID", () -> PLATFORM_BOOK.toString());
        registry.add("ELASTICSEARCH_ENABLED", () -> "false");
        registry.add("TNT_SENTIMENT_ENABLED", () -> "false");
        registry.add("JWT_AUTO_GENERATE_KEY_PAIR", () -> "true");
        registry.add("TNT_AUTH_ALLOW_ANONYMOUS", () -> "true");
        registry.add("TNT_ROLES_PROVISION_ON_STARTUP", () -> "false");
        // outbox relay tuned for a test: 1 s cycles, 1 s first backoff, PENDING taken over after 5 s
        registry.add("yow.event.outbox.relay.poll-interval-ms", () -> "1000");
        registry.add("yow.event.outbox.relay.initial-delay", () -> "1s");
        registry.add("yow.event.outbox.relay.pending-grace", () -> "5s");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    // ── terminal ports replaced by mocks (the consumers call them) ─────────────────────────────
    @MockitoBean(name = "sendNotificationUseCase") ISendNotificationUseCase sendNotification;
    // lot L3: GPS / geofence detections are delivery alerts (decision D.2), no longer incidents
    @MockitoBean com.yowyob.tiibntick.core.delivery.application.service.watch.DeliveryWatchService deliveryWatch;
    @MockitoBean DeliveryEventNotifier gofpNotifier;
    @MockitoBean MissionService agencyMissionService;
    @MockitoBean(name = "notificationStreamAdapter") NotificationStreamAdapter gofpNotificationStream;
    @MockitoBean IAnnouncementRepository gofpAnnouncementRepository;
    // lot L6: accounting books in the Kernel — its port is the terminal mock
    @MockitoBean com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingPort kernelBooks;
    @MockitoBean com.yowyob.tiibntick.core.tp.application.service.LoyaltyService loyalty;
    @MockitoBean com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService clientProfiles;
    @MockitoSpyBean com.yowyob.kernel.event.application.port.out.KafkaPublisherPort kafkaPublisherPort;

    // ── real beans ─────────────────────────────────────────────────────────────────────────────
    @MockitoBean com.yowyob.tiibntick.core.billing.wallet.application.service.PaymentLifecycleService paymentLifecycle;
    @MockitoSpyBean com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository deliveryRepository;
    @MockitoSpyBean com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository coreAnnouncements;
    @MockitoSpyBean com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository delivererRepository;
    @Autowired com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher realtimeEventPublisher;
    // lot L8 (spec §6.1): one proof per remaining consumer — the use case each consumer calls, spied
    @MockitoSpyBean com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService salesService;
    @MockitoSpyBean com.yowyob.tiibntick.core.billing.invoice.application.port.in.SystemInvoicingUseCase invoicing;
    @MockitoSpyBean com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase marketOrders;
    @MockitoSpyBean com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository entityVersions;
    @MockitoSpyBean com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelExpiryService hubExpiry;
    @MockitoSpyBean com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeSystemUseCase disputes;
    @MockitoSpyBean com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort thresholds;
    @MockitoSpyBean com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase deliveryLifecycle;
    @MockitoSpyBean com.yowyob.tiibntick.core.actor.application.port.in.ILinkFreelancerOrgUseCase freelancerOrgLinks;
    @MockitoSpyBean com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService agencyDeliverers;
    @MockitoSpyBean com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService agencyCommissions;
    @MockitoSpyBean com.yowyob.tiibntick.core.billing.report.application.service.InvoiceProjectionService invoiceReports;

    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired DeliveryEventPublisher deliveryEventPublisher;
    @Autowired PublishEventUseCase publishEvent;
    @Autowired TntAdministrationEventPublisher administrationPublisher;
    @Autowired IPermissionChangeNotifier permissionChangeNotifier;
    @Autowired PermissionCache permissionCache;
    @Autowired MissionTrackingCodeCache trackingCodeCache;
    @Autowired EventPublisher gofpEventPublisher;
    @Autowired @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate;
    @Autowired @Qualifier("tntObjectMapper") ObjectMapper mapper;
    @Autowired Collection<NewTopic> declaredTopics;
    @Autowired Map<String, org.springframework.kafka.config.AbstractKafkaListenerContainerFactory<?, ?, ?>> listenerFactories;
    @Autowired com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository envelopes;
    @Autowired com.yowyob.tiibntick.core.billing.wallet.application.port.in.ISettlementUseCase settlements;

    @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @BeforeEach
    void stubPorts() {
        when(sendNotification.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(deliveryWatch.onPosition(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Mono.empty());
        when(deliveryWatch.onGeofence(any(), any(), any())).thenReturn(Mono.empty());
        when(gofpNotifier.onDeliveryStatusChanged(any(), any(), any(), any(), any())).thenReturn(Mono.just(0));
        when(agencyMissionService.syncFromCoreStatus(any(), any(), any(), any())).thenReturn(Mono.empty());
        // lot L8: every delivery transition of the tests reaches the wallet consumer (tnt.delivery.events)
        when(paymentLifecycle.delivered(any(), any())).thenReturn(Mono.empty());
        when(paymentLifecycle.depositedAtRelay(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Mono.empty());
        when(paymentLifecycle.cancelled(any(), any(), any())).thenReturn(Mono.empty());
        when(paymentLifecycle.failed(any(), any(), any())).thenReturn(Mono.empty());
        when(kernelBooks.findOrCreateAccount(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> Mono.just(UUID.randomUUID()));
        when(kernelBooks.findOrCreateJournal(any(), any(), any(), any()))
                .thenAnswer(inv -> Mono.just(UUID.randomUUID()));
        when(kernelBooks.postEntry(any(), any(), any(), any(), any())).thenAnswer(inv -> Mono.just(
                new com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingPort.KernelEntry(
                        UUID.randomUUID(), "DRAFT")));
        when(kernelBooks.validateEntry(any(), any())).thenAnswer(inv -> Mono.just(
                new com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingPort.KernelEntry(
                        inv.getArgument(1), "VALIDATED")));
        when(kernelBooks.findEntryByReference(any(), any())).thenReturn(Mono.empty());
        when(loyalty.earn(any())).thenReturn(Mono.just(org.mockito.Mockito.mock(
                com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount.class)));
        // tp-core credits a completed delivery through its system path (lot L3), not through earn.
        when(loyalty.creditCompletedDelivery(any())).thenReturn(Mono.just(org.mockito.Mockito.mock(
                com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount.class)));
        when(clientProfiles.incrementDeliveriesIfProfiled(any(), any())).thenReturn(Mono.empty());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 1. Topology
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("no two listener containers share the same consumer group AND a topic (competing consumers)")
    void noGroupCollision() {
        Map<String, List<String>> owners = new HashMap<>();
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            String group = container.getGroupId();
            String[] topics = container.getContainerProperties().getTopics();
            if (topics == null) {
                continue;
            }
            for (String topic : topics) {
                owners.computeIfAbsent(group + " @ " + topic, k -> new ArrayList<>()).add(container.getListenerId());
            }
        }
        Map<String, List<String>> collisions = owners.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(collisions)
                .as("two listeners in the same group on the same topic split its partitions between them")
                .isEmpty();
    }

    @Test
    @DisplayName("every consumed topic is declared as a NewTopic bean (admin.auto-create=false in staging/prod)")
    void everyConsumedTopicIsDeclared() {
        Set<String> declared = declaredTopics.stream().map(NewTopic::name).collect(Collectors.toSet());
        Set<String> missing = new HashSet<>();
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            String[] topics = container.getContainerProperties().getTopics();
            if (topics == null) {
                continue;
            }
            for (String topic : topics) {
                if (!declared.contains(topic)) {
                    missing.add(topic + " (consumer " + container.getListenerId() + ")");
                }
            }
        }
        assertThat(missing).as("consumed but not declared").isEmpty();
    }

    @Test
    @DisplayName("every TntTopics constant is declared as a NewTopic bean, and nothing else (lot L8, spec annex 9.1)")
    void everyTopicConstantIsDeclared() throws Exception {
        Set<String> declared = declaredTopics.stream().map(NewTopic::name).collect(Collectors.toSet());
        Set<String> constants = new HashSet<>();
        for (java.lang.reflect.Field field : TntTopics.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                constants.add((String) field.get(null));
            }
        }
        assertThat(constants).as("TntTopics constants").hasSize(101);
        assertThat(constants.stream().filter(t -> !declared.contains(t)).collect(Collectors.toSet()))
                .as("a TntTopics constant never declared as NewTopic").isEmpty();
        // trust-core declares its own literal topics (blockchain kept aside); every other NewTopic is a constant
        assertThat(declared.stream().filter(t -> !constants.contains(t) && !t.startsWith("tnt.trust."))
                .collect(Collectors.toSet())).as("NewTopic without a TntTopics constant").isEmpty();
    }

    @Test
    @DisplayName("every listener container is running and received a partition assignment")
    void everyListenerGetsPartitions() {
        List<String> idle = new ArrayList<>();
        await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            idle.clear();
            for (MessageListenerContainer container : registry.getListenerContainers()) {
                if (!container.isRunning() || container.getAssignedPartitions() == null
                        || container.getAssignedPartitions().isEmpty()) {
                    idle.add(container.getListenerId() + " group=" + container.getGroupId()
                            + " topics=" + String.join(",", container.getContainerProperties().getTopics()));
                }
            }
            assertThat(idle).isEmpty();
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 2. Outbox (yow-event-kernel) -> Kafka
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("outbox: an envelope persisted through PublishEventUseCase is relayed to Kafka with its headers")
    void outboxRelaysToKafka() throws Exception {
        String topic = TntTopics.DELIVERY_EVENTS;
        String aggregateId = UUID.randomUUID().toString();
        String payload = "{\"probe\":\"" + aggregateId + "\"}";
        publishEvent.publish(DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString())
                .eventType("OutboxProbe")
                .aggregateId(aggregateId)
                .aggregateType("Probe")
                .tenantId(SYSTEM_TENANT)
                .solutionCode("TNT")
                .payload(payload)
                .kafkaTopic(topic)
                .occurredAt(LocalDateTime.now())
                .build()).block(Duration.ofSeconds(30));

        ConsumerRecord<String, String> record = awaitRecord(topic, r -> payload.equals(r.value()));
        assertThat(record.key()).isEqualTo(aggregateId);
        assertThat(new String(record.headers().lastHeader("X-Yow-Event-Type").value())).isEqualTo("OutboxProbe");
        assertThat(new String(record.headers().lastHeader("X-Yow-Tenant-Id").value())).isEqualTo(SYSTEM_TENANT);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 3. Delivery: mission status changed -> realtime / gofp / agency / incident
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("lot L8: a delivery transition on tnt.delivery.events (real aggregates and publisher) reaches realtime, "
            + "GOFP and agency; the parcel transition feeds the tracking-code cache")
    void missionStatusChangedReachesConsumers() {
        UUID tenantId = UUID.fromString(SYSTEM_TENANT);
        UUID sender = UUID.randomUUID();
        var parcel = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel.create(
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification(1, 10, 10, 10,
                        false, false, "Box"), tenantId, sender,
                com.yowyob.tiibntick.common.kafka.TntEventActor.of(
                        com.yowyob.tiibntick.common.kafka.TntEventActor.Type.SENDER, sender));
        var address = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress(null, null,
                "Bastos", "Yaounde", "CM",
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(3.87, 11.51));
        var delivery = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.create(tenantId, null,
                sender, parcel, address, address,
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo("Awa Ndiaye",
                        "+237600000000", null),
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency.STANDARD, null, null);
        delivery.assignDeliveryPerson(UUID.randomUUID(), null, 2.0, null);
        delivery.confirmPickup();
        UUID deliveryId = delivery.getId();

        List<com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent> events =
                new ArrayList<>(parcel.getDomainEvents());
        events.addAll(delivery.getAggregateEvents());
        deliveryEventPublisher.publishAll(events).block(Duration.ofSeconds(30));

        // realtime: the parcel events carry the code of the parcel's current delivery
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(trackingCodeCache.get(deliveryId.toString())).isEqualTo(parcel.getTrackingCode()));
        // GOFP: notifies the people involved on the notified transitions (PICKED_UP)
        verify(gofpNotifier, timeout(30_000)).onDeliveryStatusChanged(any(), eq(deliveryId), eq(tenantId),
                eq("PICKED_UP"), any());
        // agency ERP: every transition (ASSIGNED then PICKED_UP)
        verify(agencyMissionService, timeout(30_000)).syncFromCoreStatus(eq(tenantId), eq(deliveryId),
                eq("PICKED_UP"), any());
    }

    @Test
    @DisplayName("lot evt-l2: announcement published (real publisher) -> tnt.announcement.events, masked; GOFP matching runs once")
    void announcementPublishedTriggersMatchingOnce() throws Exception {
        UUID tenant = UUID.fromString(SYSTEM_TENANT);
        UUID client = UUID.randomUUID();
        var address = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress("Rue 1", null,
                "Bastos", "Yaounde", "CM",
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(3.87654, 11.51234));
        var parcel = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel.create(
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification(1, 10, 10, 10,
                        false, false, "Box"), tenant, client,
                com.yowyob.tiibntick.common.kafka.TntEventActor.of(
                        com.yowyob.tiibntick.common.kafka.TntEventActor.Type.SENDER, client));
        var announcement = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement.createDraft(
                tenant, client, "Box", "A box", java.math.BigDecimal.valueOf(2000), "XAF", parcel, address, address,
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo("Awa Ndiaye",
                        "+237600000000", null),
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency.STANDARD);
        announcement.publish();
        UUID id = announcement.getId();
        // the announcement is in the database when its event is read (DeliveryAnnouncementService saves, then
        // publishes); without it every attempt fails "not found" and is retried (lot L7 rule), which this test is not about
        org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.just(announcement))
                .when(coreAnnouncements).findById(eq(tenant), eq(id));

        // the aggregate events go out (lot L8: no legacy event any more)
        List<com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent> events =
                new ArrayList<>(announcement.getDomainEvents());
        events.addAll(parcel.getDomainEvents()); // as DeliveryAnnouncementService publishes (announcement, parcel)
        deliveryEventPublisher.publishAll(events).block(Duration.ofSeconds(30));

        ConsumerRecord<String, String> published = awaitRecord(TntTopics.ANNOUNCEMENT_EVENTS,
                r -> id.toString().equals(r.key()) && r.value().contains("AnnouncementPublished"));
        JsonNode body = mapper.readTree(published.value());
        assertThat(body.path("aggregateType").asText()).isEqualTo("ANNOUNCEMENT");
        assertThat(body.path("previousState").path("status").asText()).isEqualTo("DRAFT");
        assertThat(published.value()).doesNotContain("Rue 1").doesNotContain("+237600000000")
                .doesNotContain("Ndiaye").doesNotContain("3.87654");
        ConsumerRecord<String, String> parcelCreated = awaitRecord(TntTopics.PARCEL_EVENTS,
                r -> parcel.getId().toString().equals(r.key()) && r.value().contains("ParcelCreated"));
        assertThat(mapper.readTree(parcelCreated.value()).path("payload").path("trackingCode").asText())
                .isEqualTo(parcel.getTrackingCode());

        // GOFP matching reads the announcement once, from tnt.announcement.events
        verify(coreAnnouncements, timeout(30_000).times(1)).findById(eq(tenant), eq(id));
        verify(coreAnnouncements, org.mockito.Mockito.after(5_000).times(1)).findById(eq(tenant), eq(id));
    }

    @Test
    @DisplayName("D-150/D-148: parcel without tenant -> system tenant on tnt.parcel.events; return cancelled before "
            + "pickup -> ParcelReturnDeliveryCancelled")
    void parcelWithoutTenantAndCancelledReturnReachTheBroker() throws Exception {
        UUID tenant = UUID.fromString(SYSTEM_TENANT);
        UUID sender = UUID.randomUUID();
        var spec = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification(1, 10, 10, 10,
                false, false, "Box");
        var parcel = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel.create(spec); // no tenant
        var address = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress(null, null,
                "Bastos", "Yaounde", "CM",
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(3.87, 11.51));
        var recipient = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo("Awa Ndiaye",
                "+237600000000", null);
        var outbound = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.create(tenant, null, sender,
                parcel, address, address, recipient,
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency.STANDARD, null, null);
        outbound.assignDeliveryPerson(UUID.randomUUID(), null, 2.0, null);
        outbound.confirmPickup();
        outbound.cancel("changed my mind");
        var ret = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.create(tenant, null, sender,
                parcel, address, address, recipient,
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency.STANDARD, null, null,
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind.RETURN, outbound.getId(), 3);
        ret.assignDeliveryPerson(UUID.randomUUID(), null, 2.0, null);
        ret.cancel("no courier");

        List<com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent> events =
                new ArrayList<>(parcel.getDomainEvents());
        events.addAll(outbound.getAggregateEvents());
        events.addAll(ret.getAggregateEvents());
        deliveryEventPublisher.publishAll(events).block(Duration.ofSeconds(30));

        ConsumerRecord<String, String> created = awaitRecord(TntTopics.PARCEL_EVENTS,
                r -> parcel.getId().toString().equals(r.key()) && r.value().contains("\"ParcelCreated\""));
        assertThat(mapper.readTree(created.value()).path("tenantId").asText()).isEqualTo(SYSTEM_TENANT);
        ConsumerRecord<String, String> cancelled = awaitRecord(TntTopics.PARCEL_EVENTS,
                r -> parcel.getId().toString().equals(r.key()) && r.value().contains("ParcelReturnDeliveryCancelled"));
        JsonNode body = mapper.readTree(cancelled.value());
        assertThat(body.path("payload").path("status").asText()).isEqualTo("RETURN_REQUESTED");
        assertThat(body.path("details").path("returnCause").asText()).isEqualTo("RETURN_DELIVERY_CANCELLED");
        assertThat(body.path("tenantId").asText()).isEqualTo(SYSTEM_TENANT);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 4. Notifications — freelancer organisations
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("admin KYC approval of a freelancer org notifies the org (tnt.admin.freelancer_org.kyc_approved)")
    void kycApprovedNotifiesOrg() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orgId", orgId.toString());
        payload.put("adminId", UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("KYC_LEVEL", "BASIC");

        administrationPublisher.publish(TntTopics.ADMIN_FREELANCER_ORG_KYC_APPROVED, UUID.fromString(SYSTEM_TENANT), payload)
                .block(Duration.ofSeconds(30));

        verify(sendNotification, timeout(30_000).atLeastOnce())
                .send(anyString(), any(), eq(orgId.toString()), eq(orgId.toString()), any(), any());
    }

    @Test
    @DisplayName("lot L8: DeliveryFreelancerOrgAssigned (real aggregate and publisher) notifies the org")
    void freelancerOrgAssignedNotifiesOrg() {
        UUID tenantId = UUID.fromString(SYSTEM_TENANT);
        UUID sender = UUID.randomUUID();
        String orgId = UUID.randomUUID().toString();
        var parcel = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel.create(
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification(1, 10, 10, 10,
                        false, false, "Box"), tenantId, sender,
                com.yowyob.tiibntick.common.kafka.TntEventActor.of(
                        com.yowyob.tiibntick.common.kafka.TntEventActor.Type.SENDER, sender));
        var address = new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress(null, null,
                "Bastos", "Yaounde", "CM",
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates(3.87, 11.51));
        var delivery = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery.create(tenantId, null,
                sender, parcel, address, address,
                new com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo("Awa", "+237600000000",
                        null),
                com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency.STANDARD, null, null);
        delivery.assignToFreelancerOrg(orgId,
                com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole.OWNER);

        deliveryEventPublisher.publishAll(new ArrayList<>(delivery.getAggregateEvents()))
                .block(Duration.ofSeconds(30));

        verify(sendNotification, timeout(30_000).atLeastOnce())
                .send(anyString(), any(), eq(orgId), eq(orgId), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 5. Permissions changed -> cache invalidation
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("permission change event invalidates the local permission cache")
    void permissionChangeInvalidatesCache() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        permissionCache.put(tenant, user, Set.of("delivery:read"));
        assertThat(permissionCache.getIfPresent(tenant, user)).isNotNull();

        permissionChangeNotifier.notifyChanged(tenant, user);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(permissionCache.getIfPresent(tenant, user)).isNull());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 6. GOFP matching / subscription (JSON POJO payloads)
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GOFP matching notification is consumed and pushed to the freelancer's stream")
    void gofpMatchingNotificationIsConsumed() {
        UUID freelancerId = UUID.randomUUID();
        gofpEventPublisher.publishMatchingNotification(MatchingNotificationEvent.builder()
                .freelancerId(freelancerId).announcementId(UUID.randomUUID()).title("T").message("M").build());

        verify(gofpNotificationStream, timeout(30_000)).pushNotification(
                org.mockito.ArgumentMatchers.argThat(n -> freelancerId.equals(n.freelancerId())));
    }

    @Test
    @DisplayName("GOFP subscription attempt is consumed and processed")
    void gofpSubscriptionAttemptIsConsumed() {
        UUID announcementId = UUID.randomUUID();
        when(gofpAnnouncementRepository.findById(announcementId)).thenReturn(Mono.empty());

        gofpEventPublisher.publishSubscriptionAttempt(new SubscriptionAttemptEvent(announcementId, UUID.randomUUID(),
                Instant.now()));

        verify(gofpAnnouncementRepository, timeout(30_000)).findById(announcementId);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 7. Realtime GPS / geofence -> link + incident
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("realtime GPS position (real publisher) is materialised by Link and republished as latest snapshot")
    void gpsPositionReachesLink() {
        String missionId = UUID.randomUUID().toString();
        realtimeEventPublisher.publish(new com.yowyob.tiibntick.core.realtime.domain.event.GpsPositionUpdatedEvent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), missionId,
                com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates.of(3.848, 11.502), 25, 90, 5)).block();

        ConsumerRecord<String, String> snapshot = awaitRecord(TntTopics.LINK_TRACKING_POSITION_LATEST,
                r -> missionId.equals(r.key()));
        assertThat(snapshot.value()).contains("3.848").contains("11.502");
    }

    @Test
    @DisplayName("lot L3: realtime GPS trajectory anomaly (real publisher) reaches the delivery watch (alert, D.2)")
    void gpsAnomalyRaisesDeliveryAlert() {
        UUID missionId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        realtimeEventPublisher.publish(new com.yowyob.tiibntick.core.realtime.domain.event.GpsPositionUpdatedEvent(
                UUID.randomUUID().toString(), driverId.toString(), missionId.toString(), null,
                com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates.of(3.85, 11.5), 0, 0, 5,
                true, false, 0)).block();

        verify(deliveryWatch, timeout(30_000)).onPosition(any(), org.mockito.ArgumentMatchers.eq(missionId), any(),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    @DisplayName("lot L3: geofence DANGER_ZONE entry (real publisher, outbox) reaches the delivery watch (alert, D.2)")
    void dangerZoneGeofenceRaisesDeliveryAlert() {
        UUID missionId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        String tenant = UUID.randomUUID().toString();
        var trigger = new com.yowyob.tiibntick.core.realtime.domain.model.GeofenceTrigger(
                driverId.toString(), tenant, "zone-1", "Danger",
                com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType.DANGER_ZONE, null,
                com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceDirection.ENTER,
                com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates.of(3.9, 11.6), missionId.toString(),
                java.time.LocalDateTime.now());
        realtimeEventPublisher.publish(new com.yowyob.tiibntick.core.realtime.domain.event.GeofenceTriggerEvent(tenant, trigger)).block();

        verify(deliveryWatch, timeout(30_000)).onGeofence(any(), org.mockito.ArgumentMatchers.eq(missionId),
                org.mockito.ArgumentMatchers.eq("DANGER_ZONE"));
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 8. Incident lifecycle -> delivery, actor, wallet
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("lot evt-l2: an incident resolved on Kafka no longer drives tnt-delivery-core (IDeliveryStatusPort only)")
    void incidentResolvedNoLongerReachesDeliveryThroughKafka() throws Exception {
        // delivery-core's IncidentEventConsumer is removed (spec §6, lot L2): incident-core drives the delivery
        // in-process through IDeliveryStatusPort; a second path through the topic resumed the delivery twice.
        UUID missionId = UUID.randomUUID();
        sendIncidentEvent("RESOLVED", "AGENCY_HANDLING", missionId, UUID.randomUUID(), "AGENCY_DRIVER");

        verify(deliveryRepository, org.mockito.Mockito.after(10_000).never()).findByIdNoTenant(missionId);
    }

    @Test
    @DisplayName("lot L8: IncidentClosed on tnt.incident.events reaches tnt-actor-core, which increments the "
            + "courier's incident count")
    void incidentClosedReachesActor() throws Exception {
        UUID courier = UUID.randomUUID();
        sendIncidentEvent("CLOSED", "RESOLVED", UUID.randomUUID(), courier, "AGENCY_DRIVER");

        verify(delivererRepository, timeout(30_000)).incrementIncidentHistoryCount(courier,
                UUID.fromString(SYSTEM_TENANT));
    }

    /** An incident transition as tnt-incident-core publishes it (envelope §3.3, masked payload). */
    private void sendIncidentEvent(String status, String from, UUID deliveryId, UUID reporter, String reporterRole)
            throws Exception {
        UUID incidentId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("incidentId", incidentId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("status", status);
        payload.put("reportedByActorId", reporter.toString());
        payload.put("reportedByRole", reporterRole);
        payload.put("affectedParcelIds", List.of());
        var event = com.yowyob.tiibntick.common.kafka.TntAggregateEvent.<Map<String, Object>>builder(
                        com.yowyob.tiibntick.common.kafka.TntAggregateType.INCIDENT)
                .eventId(UUID.randomUUID())
                .eventTypeFor(status)
                .occurredAt(Instant.now())
                .aggregateId(incidentId)
                .aggregateVersion(5)
                .tenantId(SYSTEM_TENANT)
                .correlationId(incidentId.toString())
                .trigger("TEST")
                .actor(com.yowyob.tiibntick.common.kafka.TntEventActor.system())
                .previousState(com.yowyob.tiibntick.common.kafka.TntAggregateEvent.previousState(from, null))
                .payload(payload)
                .build();
        kafkaTemplate.send(new org.apache.kafka.clients.producer.ProducerRecord<>(TntTopics.INCIDENT_EVENTS,
                incidentId.toString(), com.yowyob.tiibntick.common.kafka.TntEventJson.write(event)))
                .get(30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("lot L5: a DisputeOpen on tnt.dispute.events freezes the escrow of the delivery (wallet consumer)")
    void disputeOpenFreezesEscrow() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        when(paymentLifecycle.disputeOpened(any(), any())).thenReturn(Mono.empty());
        String event = "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"DisputeOpen\","
                + "\"aggregateType\":\"DISPUTE\",\"aggregateId\":\"" + disputeId + "\",\"tenantId\":\""
                + UUID.randomUUID() + "\",\"payload\":{\"disputeId\":\"" + disputeId + "\",\"deliveryId\":\""
                + deliveryId + "\",\"status\":\"OPEN\"},\"details\":{}}";
        kafkaTemplate.send(TntTopics.DISPUTE_EVENTS, disputeId.toString(), event).get(30, TimeUnit.SECONDS);

        verify(paymentLifecycle, timeout(30_000)).disputeOpened(deliveryId, disputeId);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 9. Reliability decisions of 2026-11-26: start offsets, duplicate filter, relay, wallet events
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a new consumer group starts at the BEGINNING of its topics (earliest) everywhere")
    void everyGroupStartsAtEarliest() {
        List<String> notEarliest = new ArrayList<>();
        listenerFactories.forEach((name, factory) -> {
            Object reset = factory.getConsumerFactory().getConfigurationProperties()
                    .get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
            if (!"earliest".equals(String.valueOf(reset))) {
                notEarliest.add("factory " + name + " -> " + reset);
            }
        });
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            String override = container.getContainerProperties().getKafkaConsumerProperties()
                    .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
            // documented exception: the per-instance permission-cache group (a fresh group per boot,
            // only live invalidations matter — see kafka-async-wiring.md §8.4)
            // lot evt-l2: same pattern for the delivery thresholds cache (DeliveryThresholdsChangedConsumer)
            boolean perInstanceCacheGroup = container.getGroupId() != null
                    && (container.getGroupId().startsWith("tnt-roles-cache-")
                    || container.getGroupId().startsWith("tnt-delivery-thresholds-cache-"));
            if (override != null && !"earliest".equals(override) && !perInstanceCacheGroup) {
                notEarliest.add("listener " + container.getListenerId() + " -> " + override);
            }
        }
        assertThat(notEarliest).isEmpty();
    }

    @Test
    @DisplayName("every listener container factory (modules + Boot default) carries the duplicate filter")
    void everyFactoryHasTheDuplicateFilter() {
        assertThat(listenerFactories).containsKey("kafkaListenerContainerFactory")
                .containsKey("gofpJsonKafkaListenerContainerFactory");
        List<String> unfiltered = listenerFactories.entrySet().stream()
                .filter(e -> !(e.getValue().getRecordInterceptor()
                        instanceof com.yowyob.kernel.event.adapter.kafka.consumer.IdempotentRecordInterceptor)
                        && !(e.getValue().getRecordInterceptor()
                        instanceof org.springframework.kafka.listener.CompositeRecordInterceptor))
                .map(Map.Entry::getKey).toList();
        assertThat(unfiltered).isEmpty();
    }

    @Test
    @DisplayName("duplicate: a notification event delivered twice notifies the org once")
    void duplicateNotificationNotifiesOnce() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID sentinelOrg = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orgId", orgId.toString());
        payload.put("adminId", UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now().toString());
        administrationPublisher.publish(TntTopics.ADMIN_FREELANCER_ORG_KYC_APPROVED, UUID.fromString(SYSTEM_TENANT), payload)
                .block(Duration.ofSeconds(30));
        ConsumerRecord<String, String> original = awaitRecord(TntTopics.ADMIN_FREELANCER_ORG_KYC_APPROVED,
                r -> r.value().contains(orgId.toString()));

        resend(original);
        sendSentinel(original, orgId.toString(), sentinelOrg.toString());

        // one KYC approval sends on several channels: the duplicated org must get exactly as many
        // notifications as the sentinel org, which received its event once
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            long sentinelSends = notificationsTo(sentinelOrg.toString());
            assertThat(sentinelSends).isPositive();
            assertThat(notificationsTo(orgId.toString())).isEqualTo(sentinelSends);
        });
    }

    private long notificationsTo(String recipient) {
        return org.mockito.Mockito.mockingDetails(sendNotification).getInvocations().stream()
                .filter(i -> "send".equals(i.getMethod().getName()) && recipient.equals(i.getArgument(2)))
                .count();
    }

    @Test
    @DisplayName("duplicate: a DeliveryDelivered delivered twice credits loyalty points once (lot L8)")
    void duplicateMissionCompletedCreditsLoyaltyOnce() throws Exception {
        UUID thirdParty = UUID.randomUUID();
        String missionId = UUID.randomUUID().toString();
        String sentinelMission = UUID.randomUUID().toString();
        String value = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"DeliveryDelivered\","
                + "\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\",\"aggregateId\":\"" + missionId + "\","
                + "\"tenantId\":\"" + SYSTEM_TENANT + "\",\"previousState\":{\"status\":\"OUT_FOR_DELIVERY\"},"
                + "\"payload\":{\"deliveryId\":\"" + missionId + "\",\"status\":\"DELIVERED\","
                + "\"senderId\":\"" + thirdParty + "\"}}";
        String key = missionId;
        kafkaTemplate.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                TntTopics.DELIVERY_EVENTS, key, value)).get(30, TimeUnit.SECONDS);
        ConsumerRecord<String, String> original = awaitRecord(TntTopics.DELIVERY_EVENTS,
                r -> value.equals(r.value()));

        resend(original);
        sendSentinel(original, missionId, sentinelMission);

        verify(loyalty, timeout(30_000)).creditCompletedDelivery(org.mockito.ArgumentMatchers.argThat(c -> c != null
                && sentinelMission.equals(c.deliveryId())));
        verify(loyalty, org.mockito.Mockito.times(1)).creditCompletedDelivery(org.mockito.ArgumentMatchers.argThat(c -> c != null
                && missionId.equals(c.deliveryId())));
    }

    @Test
    @DisplayName("duplicate: a geofence crossing delivered twice raises one delivery alert evaluation")
    void duplicateGeofenceIsHandledOnce() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID sentinelMission = UUID.randomUUID();
        var trigger = new com.yowyob.tiibntick.core.realtime.domain.model.GeofenceTrigger(
                UUID.randomUUID().toString(), SYSTEM_TENANT, "zone-dup", "Danger",
                com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType.DANGER_ZONE, null,
                com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceDirection.ENTER,
                com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates.of(3.9, 11.6), missionId.toString(),
                java.time.LocalDateTime.now());
        realtimeEventPublisher.publish(new com.yowyob.tiibntick.core.realtime.domain.event.GeofenceTriggerEvent(
                SYSTEM_TENANT, trigger)).block(Duration.ofSeconds(30));
        ConsumerRecord<String, String> original = awaitRecord(TntTopics.REALTIME_GEOFENCE_TRIGGERED,
                r -> r.value().contains(missionId.toString()));
        assertThat(original.headers().lastHeader("X-Yow-Envelope-Id")).as("outbox envelope id header").isNotNull();

        resend(original);
        sendSentinel(original, missionId.toString(), sentinelMission.toString());

        verify(deliveryWatch, timeout(30_000)).onGeofence(any(), org.mockito.ArgumentMatchers.eq(sentinelMission), any());
        verify(deliveryWatch, org.mockito.Mockito.times(1)).onGeofence(any(), org.mockito.ArgumentMatchers.eq(missionId),
                any());
    }

    @Test
    @DisplayName("R1 / lot L6: an escrow settlement publishes payment.confirmed + commission.calculated (SETTLED); "
            + "accounting books the commission once in the platform's Kernel book, even when the event is delivered twice")
    void settlementEventsReachAccountingOnce() throws Exception {
        UUID delivery = UUID.randomUUID();
        var settlement = settlements.record(new com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand(
                UUID.fromString(SYSTEM_TENANT), "DELIVERY", delivery, UUID.randomUUID(), UUID.randomUUID(),
                new java.math.BigDecimal("5000"), "XAF",
                com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod.ESCROW,
                com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus.PAID, "esc-" + delivery,
                new java.math.BigDecimal("0.05"))).block(Duration.ofSeconds(30));
        String settlementId = settlement.getId().toString();
        String reference = "COM-" + delivery;

        verify(kernelBooks, timeout(30_000)).postEntry(org.mockito.ArgumentMatchers.eq(PLATFORM_BOOK), any(),
                org.mockito.ArgumentMatchers.eq(reference), any(),
                org.mockito.ArgumentMatchers.argThat(lines -> lines != null && lines.size() == 2
                        && lines.get(0).debit().compareTo(new java.math.BigDecimal("250")) == 0));
        verify(kernelBooks, timeout(30_000)).validateEntry(org.mockito.ArgumentMatchers.eq(PLATFORM_BOOK), any());

        ConsumerRecord<String, String> commission = awaitRecord(TntTopics.BILLING_WALLET_COMMISSION_CALCULATED,
                r -> r.value().contains(settlementId));
        String sentinel = UUID.randomUUID().toString();
        resend(commission);
        sendSentinel(commission, settlementId, sentinel);

        // the sentinel (another settlement id, same delivery) is booked; the duplicate before it was not
        verify(kernelBooks, timeout(30_000).times(2)).postEntry(any(), any(),
                org.mockito.ArgumentMatchers.eq(reference), any(), any());
        verify(kernelBooks, org.mockito.Mockito.times(2)).postEntry(any(), any(),
                org.mockito.ArgumentMatchers.eq(reference), any(), any());
    }

    @Test
    @DisplayName("R2: a publish that fails (simulated Kafka outage) leaves the envelope FAILED; the relay re-publishes it")
    void relayRepublishesFailedEnvelope() {
        var id = com.yowyob.kernel.event.domain.vo.EnvelopeId.generate();
        String payload = "{\"probe\":\"relay-" + id.value() + "\"}";
        org.mockito.Mockito.doReturn(Mono.error(new IllegalStateException("simulated Kafka outage")))
                .doCallRealMethod()
                .when(kafkaPublisherPort).publish(
                        org.mockito.ArgumentMatchers.argThat(e -> e != null && id.equals(e.getId())),
                        org.mockito.ArgumentMatchers.anyMap());

        publishEvent.publish(probeEnvelope(id, payload)).block(Duration.ofSeconds(30));
        assertThat(envelopes.findById(id, null).block().getStatus())
                .isEqualTo(com.yowyob.kernel.event.domain.enums.EnvelopeStatus.FAILED);

        ConsumerRecord<String, String> record = awaitRecord(TntTopics.DELIVERY_EVENTS, r -> payload.equals(r.value()));
        assertThat(new String(record.headers().lastHeader("X-Yow-Envelope-Id").value())).isEqualTo(id.value());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var stored = envelopes.findById(id, null).block();
            assertThat(stored.getStatus()).isEqualTo(com.yowyob.kernel.event.domain.enums.EnvelopeStatus.PUBLISHED);
            assertThat(stored.getRetryCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("R2: an envelope left PENDING (process died before publishing) is taken over by the relay")
    void relayTakesOverStalePending() {
        var id = com.yowyob.kernel.event.domain.vo.EnvelopeId.generate();
        String payload = "{\"probe\":\"pending-" + id.value() + "\"}";
        envelopes.save(probeEnvelope(id, payload)).block(Duration.ofSeconds(30));   // persisted, never sent

        awaitRecord(TntTopics.DELIVERY_EVENTS, r -> payload.equals(r.value()));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(envelopes.findById(id, null).block()
                .getStatus()).isEqualTo(com.yowyob.kernel.event.domain.enums.EnvelopeStatus.PUBLISHED));
    }

    private DomainEventEnvelope probeEnvelope(com.yowyob.kernel.event.domain.vo.EnvelopeId id, String payload) {
        return DomainEventEnvelope.wrap()
                .id(id)
                .correlationId(UUID.randomUUID().toString())
                .eventType("RelayProbe")
                .aggregateId(id.value())
                .aggregateType("Probe")
                .tenantId(SYSTEM_TENANT)
                .solutionCode("TNT")
                .payload(payload)
                .kafkaTopic(TntTopics.DELIVERY_EVENTS)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    /** Sends the very same record again (same key, value and headers — same event id). */
    private void resend(ConsumerRecord<String, String> record) throws Exception {
        var copy = new org.apache.kafka.clients.producer.ProducerRecord<>(record.topic(), null, record.key(),
                record.value(), record.headers());
        kafkaTemplate.send(copy).get(30, TimeUnit.SECONDS);
    }

    /**
     * Sends, after the duplicate and on the same partition (same key), a distinct event whose effect
     * is observable: once it is processed, the duplicate before it has been handled too.
     */
    private void sendSentinel(ConsumerRecord<String, String> record, String from, String to) throws Exception {
        var headers = new org.apache.kafka.common.header.internals.RecordHeaders();
        record.headers().forEach(h -> {
            if (!"X-Yow-Envelope-Id".equals(h.key()) && !"X-Yow-Event-Id".equals(h.key())) {
                headers.add(h);
            }
        });
        headers.add("X-Yow-Event-Id", UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var sentinel = new org.apache.kafka.clients.producer.ProducerRecord<>(record.topic(), null, record.key(),
                record.value().replace(from, to), headers);
        kafkaTemplate.send(sentinel).get(30, TimeUnit.SECONDS);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // 10. Lot L8 (spec §6.1): every remaining consumer, one proof each (record on the topic -> use case called)
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("lot L8: a DeliveryDelivered on tnt.delivery.events reaches wallet, sales, invoicing, market and sync")
    void deliveredReachesEveryDeliveryConsumer() throws Exception {
        UUID delivery = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID tenant = UUID.fromString(SYSTEM_TENANT);
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(salesService)).followDelivery(any(), any(), any(), any(), any());
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(invoicing)).generateOnceAsSystem(any());
        Map<String, Object> payload = new HashMap<>();
        payload.put("deliveryId", delivery.toString());
        payload.put("status", "DELIVERED");
        payload.put("senderId", sender.toString());
        payload.put("agreedAmount", 2500);
        payload.put("currency", "XAF");
        kafkaTemplate.send(TntTopics.DELIVERY_EVENTS, delivery.toString(),
                aggregateJson("DELIVERY", "DeliveryDelivered", delivery, "OUT_FOR_DELIVERY", payload, Map.of()))
                .get(30, TimeUnit.SECONDS);

        verify(paymentLifecycle, timeout(30_000)).delivered(eq(delivery), any());
        verify(spied(salesService), timeout(30_000)).followDelivery(eq(tenant), eq(delivery), eq("DeliveryDelivered"),
                any(), any());
        verify(spied(invoicing), timeout(30_000)).generateOnceAsSystem(org.mockito.ArgumentMatchers.argThat(c -> c != null
                && delivery.toString().equals(c.deliveryId())));
        verify(spied(marketOrders), timeout(30_000)).getOrdersByDelivery(delivery, SYSTEM_TENANT);
        verify(spied(entityVersions), timeout(30_000)).upsert(org.mockito.ArgumentMatchers.argThat(r -> r != null
                && delivery.toString().equals(r.aggregateId()) && "DELIVERY".equals(r.aggregateType())));
    }

    @Test
    @DisplayName("lot L8: a DeliveryAlertRaised DELIVERY_LATE on tnt.delivery.events reaches the GOFP notifier")
    void lateAlertReachesGofp() throws Exception {
        UUID delivery = UUID.randomUUID();
        when(gofpNotifier.onDeliveryLate(any(), any(), any(), any())).thenReturn(Mono.just(1));
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "IN_TRANSIT");
        payload.put("trackingCode", "TNT-LATE-1");
        kafkaTemplate.send(TntTopics.DELIVERY_EVENTS, delivery.toString(), aggregateJson("DELIVERY",
                "DeliveryAlertRaised", delivery, "IN_TRANSIT", payload, Map.of("alertType", "DELIVERY_LATE")))
                .get(30, TimeUnit.SECONDS);

        verify(gofpNotifier, timeout(30_000)).onDeliveryLate(any(), eq(delivery),
                eq(UUID.fromString(SYSTEM_TENANT)), eq("TNT-LATE-1"));
    }

    @Test
    @DisplayName("lot L8: a parcel not collected at the relay (tnt.parcel.events) expires the agency hub parcel")
    void notCollectedParcelExpiresTheAgencyHubParcel() throws Exception {
        UUID parcel = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(hubExpiry)).expireDeposited(any(), any(), any());
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "RETURN_REQUESTED");
        payload.put("returnCause", "NOT_COLLECTED_AT_RELAY");
        payload.put("trackingCode", "TNT-HUB-1");
        Map<String, Object> event = mapper.readValue(aggregateJson("PARCEL", "ParcelReturnRequested", parcel, null,
                payload, Map.of()), Map.class);
        event.put("previousState", Map.of("status", "AT_RELAY_HUB", "custody", Map.of("hubId", UUID.randomUUID().toString())));
        kafkaTemplate.send(TntTopics.PARCEL_EVENTS, parcel.toString(), mapper.writeValueAsString(event))
                .get(30, TimeUnit.SECONDS);

        verify(spied(hubExpiry), timeout(30_000)).expireDeposited(eq("TNT-HUB-1"), eq(UUID.fromString(SYSTEM_TENANT)), any());
    }

    @Test
    @DisplayName("lot L8: IncidentEscalatedToDispute on tnt.incident.events opens the dispute (dispute-core)")
    void escalatedIncidentOpensADispute() throws Exception {
        UUID delivery = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(disputes)).openFromIncident(any());
        sendIncidentEvent("ESCALATED_TO_DISPUTE", "AGENCY_HANDLING", delivery, UUID.randomUUID(), "AGENCY_DRIVER");

        verify(spied(disputes), timeout(30_000)).openFromIncident(org.mockito.ArgumentMatchers.argThat(c -> c != null
                && delivery.toString().equals(c.missionId())));
    }

    @Test
    @DisplayName("lot L8: tnt.billing.compensation.paid confirms the dispute and is booked by accounting")
    void compensationPaidReachesDisputeAndAccounting() throws Exception {
        String dispute = UUID.randomUUID().toString();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(disputes)).confirmCompensationPaid(any());
        Map<String, Object> paid = new HashMap<>();
        paid.put("eventId", UUID.randomUUID().toString());
        paid.put("compensationId", UUID.randomUUID().toString());
        paid.put("disputeId", dispute);
        paid.put("tenantId", SYSTEM_TENANT);
        paid.put("paymentReference", "PAY-" + dispute);
        paid.put("amount", 1500);
        paid.put("currency", "XAF");
        paid.put("occurredAt", Instant.now().toString());
        kafkaTemplate.send(TntTopics.BILLING_COMPENSATION_PAID, dispute, mapper.writeValueAsString(paid))
                .get(30, TimeUnit.SECONDS);

        verify(spied(disputes), timeout(30_000)).confirmCompensationPaid(org.mockito.ArgumentMatchers.argThat(c -> c != null
                && ("PAY-" + dispute).equals(c.paymentReference())));
        verify(kernelBooks, timeout(30_000)).postEntry(any(), any(), eq("IND-" + dispute), any(), any());
    }

    @Test
    @DisplayName("lot L8: tnt.billing.invoice.paid is booked in the issuer's book; tnt.billing.invoice.events is "
            + "projected by the report")
    void invoiceEventsReachAccountingAndReport() throws Exception {
        String invoice = UUID.randomUUID().toString();
        UUID issuer = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(invoiceReports)).updateStatus(any(), any(), any(), any());
        Map<String, Object> paid = new HashMap<>();
        paid.put("eventId", UUID.randomUUID().toString());
        paid.put("invoiceId", invoice);
        paid.put("tenantId", SYSTEM_TENANT);
        paid.put("issuerOrgId", issuer.toString());
        paid.put("amount", Map.of("amount", 3000, "currency", "XAF"));
        paid.put("occurredAt", Instant.now().toString());
        String body = mapper.writeValueAsString(paid);
        kafkaTemplate.send(TntTopics.BILLING_INVOICE_PAID, invoice, body).get(30, TimeUnit.SECONDS);
        var record = new org.apache.kafka.clients.producer.ProducerRecord<>(TntTopics.BILLING_INVOICE_EVENTS, invoice,
                body);
        record.headers().add("eventType", "InvoicePaid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(30, TimeUnit.SECONDS);

        verify(kernelBooks, timeout(30_000)).postEntry(eq(issuer), any(), eq("FAC-" + invoice), any(), any());
        verify(spied(invoiceReports), timeout(30_000)).updateStatus(eq(UUID.fromString(SYSTEM_TENANT)),
                eq(UUID.fromString(invoice)),
                eq(com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus.PAID), any());
    }

    @Test
    @DisplayName("lot L8: tnt.billing.wallet.payment.confirmed reaches the invoice, the agency commission and market")
    void paymentConfirmedReachesInvoiceCommissionAndMarket() throws Exception {
        UUID invoice = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(invoicing)).markPaidAsSystem(any());
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(agencyCommissions)).confirmWalletPayment(any(), any());
        // an unknown order is an error (retried, then tnt.dlq): the order lookup is the observed effect
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(marketOrders)).getOrder(any(), any());
        Map<String, Object> confirmed = new HashMap<>();
        confirmed.put("invoiceId", invoice.toString());
        confirmed.put("orderId", order.toString());
        confirmed.put("tenantId", SYSTEM_TENANT);
        confirmed.put("paymentRef", "PAY-" + invoice);
        kafkaTemplate.send(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, invoice.toString(),
                mapper.writeValueAsString(confirmed)).get(30, TimeUnit.SECONDS);

        verify(spied(invoicing), timeout(30_000)).markPaidAsSystem(org.mockito.ArgumentMatchers.argThat(c -> c != null
                && invoice.equals(c.invoiceId())));
        verify(spied(agencyCommissions), timeout(30_000)).confirmWalletPayment(UUID.fromString(SYSTEM_TENANT), invoice);
        verify(spied(marketOrders), timeout(30_000)).getOrder(order, SYSTEM_TENANT);
    }

    @Test
    @DisplayName("lot L8: a delivery-thresholds change (real publisher, tnt.administration.events) clears delivery's cache")
    void thresholdsChangeClearsTheDeliveryCache() throws Exception {
        Map<String, Object> change = new HashMap<>();
        change.put("eventType", "DeliveryThresholdsChanged");
        change.put("scope", "PLATFORM");
        change.put("change", "UPDATED");
        kafkaTemplate.send(TntTopics.ADMINISTRATION_EVENTS, UUID.randomUUID().toString(),
                mapper.writeValueAsString(change)).get(30, TimeUnit.SECONDS);

        verify(spied(thresholds), timeout(30_000).atLeastOnce()).invalidateAll();
    }

    @Test
    @DisplayName("lot L8: tnt.vehicle.assigned_to_mission records the vehicle on the delivery")
    void vehicleAssignedReachesTheDelivery() throws Exception {
        UUID delivery = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(deliveryLifecycle))
                .recordFreelancerVehicleAssigned(any(), any(), any());
        kafkaTemplate.send(TntTopics.VEHICLE_ASSIGNED_TO_MISSION, delivery.toString(), "{\"missionId\":\"" + delivery
                + "\",\"vehicleId\":\"veh-1\",\"activeEquipmentIds\":[\"eq-1\"]}").get(30, TimeUnit.SECONDS);

        verify(spied(deliveryLifecycle), timeout(30_000)).recordFreelancerVehicleAssigned(eq(delivery), eq("veh-1"),
                eq(List.of("eq-1")));
    }

    @Test
    @DisplayName("lot L8: tnt.freelancer_org.created / .verified reach tnt-actor-core (owner linked, org verified)")
    void freelancerOrgEventsReachActor() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(freelancerOrgLinks)).linkToFreelancerOrg(any());
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(freelancerOrgLinks)).updateOrgVerificationStatus(any(),
                org.mockito.ArgumentMatchers.anyBoolean());
        kafkaTemplate.send(TntTopics.FREELANCER_ORG_CREATED, orgId.toString(), "{\"orgId\":\"" + orgId
                + "\",\"ownerActorId\":\"" + owner + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}")
                .get(30, TimeUnit.SECONDS);
        kafkaTemplate.send(TntTopics.FREELANCER_ORG_VERIFIED, orgId.toString(), "{\"orgId\":\"" + orgId + "\"}")
                .get(30, TimeUnit.SECONDS);

        verify(spied(freelancerOrgLinks), timeout(30_000)).linkToFreelancerOrg(org.mockito.ArgumentMatchers.argThat(c ->
                c != null && owner.equals(c.actorId()) && orgId.equals(c.freelancerOrgId())));
        verify(spied(freelancerOrgLinks), timeout(30_000)).updateOrgVerificationStatus(orgId, true);
    }

    @Test
    @DisplayName("lot L8: tnt.actor.kyc.validated reactivates the agency deliverer (agency workforce)")
    void kycValidatedReachesTheAgency() throws Exception {
        UUID actor = UUID.randomUUID();
        org.mockito.Mockito.doReturn(Mono.empty()).when(spied(agencyDeliverers)).reactivateByActorId(any(), any());
        kafkaTemplate.send(TntTopics.ACTOR_KYC_VALIDATED, actor.toString(), "{\"actorId\":\"" + actor
                + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}").get(30, TimeUnit.SECONDS);

        verify(spied(agencyDeliverers), timeout(30_000)).reactivateByActorId(UUID.fromString(SYSTEM_TENANT), actor);
    }

    @Test
    @DisplayName("lot L8: KYC rejected, suspended, unsuspended and template applied notify their recipient")
    void remainingNotifyTopicsNotify() throws Exception {
        for (String topic : List.of(TntTopics.ADMIN_FREELANCER_ORG_KYC_REJECTED, TntTopics.ADMIN_FREELANCER_ORG_SUSPENDED,
                TntTopics.ADMIN_FREELANCER_ORG_UNSUSPENDED)) {
            UUID orgId = UUID.randomUUID();
            Map<String, Object> payload = new HashMap<>();
            payload.put("orgId", orgId.toString());
            payload.put("adminId", UUID.randomUUID().toString());
            payload.put("reason", "test");
            payload.put("occurredAt", Instant.now().toString());
            administrationPublisher.publish(topic, UUID.fromString(SYSTEM_TENANT), payload).block(Duration.ofSeconds(30));
            verify(sendNotification, timeout(30_000).atLeastOnce().description(topic))
                    .send(anyString(), any(), eq(orgId.toString()), eq(orgId.toString()), any(), any());
        }
        String owner = UUID.randomUUID().toString();
        kafkaTemplate.send(TntTopics.BILLING_TEMPLATE_APPLIED, owner, "{\"ownerActorId\":\"" + owner
                + "\",\"templateCode\":\"TPL-1\",\"createdPolicyId\":\"p-1\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}")
                .get(30, TimeUnit.SECONDS);
        verify(sendNotification, timeout(30_000)).send(anyString(), any(), eq(owner), eq(owner), any(), any());
    }

    /** The Mockito spy behind a Spring AOP proxy (a @Transactional service is proxied around its spy). */
    private static <T> T spied(T bean) {
        return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(bean);
    }

    /** An aggregate event envelope (spec §3.3) as the core publishes it. */
    private String aggregateJson(String aggregateType, String eventType, UUID aggregateId, String previousStatus,
                                 Map<String, Object> payload, Map<String, Object> details) throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put("schemaVersion", 1);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("aggregateType", aggregateType);
        event.put("aggregateId", aggregateId.toString());
        event.put("aggregateVersion", 3);
        event.put("tenantId", SYSTEM_TENANT);
        event.put("occurredAt", Instant.now().toString());
        event.put("previousState", previousStatus == null ? null : Map.of("status", previousStatus));
        event.put("payload", payload);
        event.put("details", details);
        return mapper.writeValueAsString(event);
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> awaitRecord(String topic,
                                                       java.util.function.Predicate<ConsumerRecord<String, String>> match) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-it-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no matching record on topic " + topic + " within 60s");
    }
}
