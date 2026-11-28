package com.yowyob.tiibntick.bootstrap.messaging;

import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.actor.application.service.FreelancerOrgLinkService;
import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryLifecycleService;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketOrderResponse;
import com.yowyob.tiibntick.core.marketback.application.service.MarketOrderApplicationService;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (spec §3.10, §6): proof on a REAL broker and the REAL assembled context that the formerly non-blocking
 * consumers now await their work, are deduplicated, and dead-letter a failing record to {@code tnt.dlq} after the
 * configured attempts (4) instead of losing it.
 *
 * <p>For each consumer: (1) a record produced on its real topic reaches the use case it calls; (2) the very same
 * record delivered twice (same {@code X-Yow-Event-Id}) has one effect, proven with a sentinel sent after the
 * duplicate on the same partition; (3) when the use case fails, the record is retried then published to
 * {@code tnt.dlq} with the consumer's group in {@code kafka_dlt-original-consumer-group}.
 *
 * <p>Same live infrastructure as {@link KafkaAsyncWiringTest} (skipped unless {@code TNT_IT_KAFKA_BOOTSTRAP} is set):
 * <pre>
 * TNT_IT_KAFKA_BOOTSTRAP=localhost:19092 TNT_IT_DB_PORT=15433 TNT_IT_DB_PASSWORD=... TNT_IT_REDIS_PORT=16379 \
 *   mvn -o test -pl tnt-bootstrap -am -Dtest=KafkaBlockingConsumersWiringTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * The use cases at the end of each consumer are mocks; the broker, the listener containers, the error handler, the
 * idempotence store and the dead-letter publication are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@DisplayName("Kafka blocking consumers (lot L7) — real broker, real context")
class KafkaBlockingConsumersWiringTest {

    private static final String SYSTEM_TENANT = "d3b07384-d9a0-4c9c-b7d1-5f2a1b6c0e91";
    private static final String DLT_GROUP_HEADER = "kafka_dlt-original-consumer-group";
    private static final int ATTEMPTS = 4;

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
        registry.add("ELASTICSEARCH_ENABLED", () -> "false");
        registry.add("TNT_SENTIMENT_ENABLED", () -> "false");
        registry.add("JWT_AUTO_GENERATE_KEY_PAIR", () -> "true");
        registry.add("TNT_AUTH_ALLOW_ANONYMOUS", () -> "true");
        registry.add("TNT_ROLES_PROVISION_ON_STARTUP", () -> "false");
        // the hourly agency job must not run during the test
        registry.add("tnt.agency.hub.expiry-job.enabled", () -> "false");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    // ── the use cases each consumer calls ──────────────────────────────────────────────────────
    @MockitoBean CommissionService commissions;
    @MockitoBean AgencyDelivererService deliverers;
    @MockitoBean MarketOrderApplicationService marketOrders;
    @MockitoBean FreelancerOrgLinkService freelancerOrgLinks;
    @MockitoBean DeliveryLifecycleService deliveryLifecycle;
    @MockitoBean(name = "sendNotificationUseCase") ISendNotificationUseCase notifications;
    @MockitoBean IAnnouncementRepository gofpAnnouncements;

    @Autowired @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @BeforeEach
    void stubPorts() {
        when(notifications.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // agency-commission — WalletPayoutConsumer
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("WalletPayoutConsumer: confirmation applied once even when delivered twice")
    void walletPayoutOnce() throws Exception {
        UUID commission = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        when(commissions.confirmWalletPayment(any(), any())).thenReturn(Mono.empty());

        sendTwiceThenSentinel(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED,
                "{\"invoiceId\":\"" + commission + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}",
                commission.toString(), sentinel.toString());

        verify(commissions, timeout(30_000)).confirmWalletPayment(UUID.fromString(SYSTEM_TENANT), sentinel);
        verify(commissions, times(1)).confirmWalletPayment(UUID.fromString(SYSTEM_TENANT), commission);
    }

    @Test
    @DisplayName("WalletPayoutConsumer: a failing confirmation is retried then dead-lettered")
    void walletPayoutDeadLettered() throws Exception {
        UUID commission = UUID.randomUUID();
        when(commissions.confirmWalletPayment(any(), eq(commission))).thenReturn(Mono.error(new IllegalStateException("db")));

        String value = "{\"invoiceId\":\"" + commission + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}";
        send(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, value);

        awaitDeadLetter("tnt-agency-erp-billing-consumer", value);
        verify(commissions, times(ATTEMPTS)).confirmWalletPayment(any(), eq(commission));
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // agency-workforce — ActorKycConsumer
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ActorKycConsumer: reactivation once, failure dead-lettered")
    void actorKyc() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        when(deliverers.reactivateByActorId(any(), any())).thenReturn(Mono.empty());
        when(deliverers.reactivateByActorId(any(), eq(failing))).thenReturn(Mono.error(new IllegalStateException("db")));

        sendTwiceThenSentinel(TntTopics.ACTOR_KYC_VALIDATED,
                "{\"actorId\":\"" + actor + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}",
                actor.toString(), sentinel.toString());
        verify(deliverers, timeout(30_000)).reactivateByActorId(UUID.fromString(SYSTEM_TENANT), sentinel);
        verify(deliverers, times(1)).reactivateByActorId(UUID.fromString(SYSTEM_TENANT), actor);

        String value = "{\"actorId\":\"" + failing + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}";
        send(TntTopics.ACTOR_KYC_VALIDATED, value);
        awaitDeadLetter("tnt-agency-erp-actor-consumer", value);
        verify(deliverers, times(ATTEMPTS)).reactivateByActorId(any(), eq(failing));
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // market-back — MarketKafkaConsumer.onPaymentConfirmed
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("MarketKafkaConsumer: a paid DRAFT order is confirmed once, a failure is dead-lettered")
    void marketPayment() throws Exception {
        UUID order = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        MarketOrderResponse draft = mock(MarketOrderResponse.class);
        when(draft.status()).thenReturn(OrderStatus.DRAFT);
        when(marketOrders.getOrder(any(), anyString())).thenReturn(Mono.just(draft));
        when(marketOrders.confirmOrder(any(), anyString())).thenReturn(Mono.just(draft));
        when(marketOrders.getOrder(eq(failing), anyString())).thenReturn(Mono.error(new IllegalStateException("db")));

        sendTwiceThenSentinel(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED,
                "{\"orderId\":\"" + order + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}",
                order.toString(), sentinel.toString());
        verify(marketOrders, timeout(30_000)).confirmOrder(sentinel, SYSTEM_TENANT);
        verify(marketOrders, times(1)).confirmOrder(order, SYSTEM_TENANT);

        String value = "{\"orderId\":\"" + failing + "\",\"tenantId\":\"" + SYSTEM_TENANT + "\"}";
        send(TntTopics.BILLING_WALLET_PAYMENT_CONFIRMED, value);
        awaitDeadLetter("tnt-market-group", value);
        verify(marketOrders, times(ATTEMPTS)).getOrder(eq(failing), anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // actor-core — FreelancerOrgEventConsumer
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FreelancerOrgEventConsumer: verification applied once, failure dead-lettered")
    void freelancerOrg() throws Exception {
        UUID org = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        when(freelancerOrgLinks.updateOrgVerificationStatus(any(), eq(true))).thenReturn(Mono.empty());
        when(freelancerOrgLinks.updateOrgVerificationStatus(eq(failing), eq(true)))
                .thenReturn(Mono.error(new IllegalStateException("db")));

        sendTwiceThenSentinel(TntTopics.FREELANCER_ORG_VERIFIED, "{\"orgId\":\"" + org + "\"}",
                org.toString(), sentinel.toString());
        verify(freelancerOrgLinks, timeout(30_000)).updateOrgVerificationStatus(sentinel, true);
        verify(freelancerOrgLinks, times(1)).updateOrgVerificationStatus(org, true);

        String value = "{\"orgId\":\"" + failing + "\"}";
        send(TntTopics.FREELANCER_ORG_VERIFIED, value);
        awaitDeadLetter("tnt-actor-freelancer-org-consumer", value);
        verify(freelancerOrgLinks, times(ATTEMPTS)).updateOrgVerificationStatus(failing, true);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // delivery-core — FreelancerVehicleEventConsumer
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FreelancerVehicleEventConsumer: vehicle recorded once, unknown delivery dead-lettered")
    void freelancerVehicle() throws Exception {
        UUID delivery = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(deliveryLifecycle.recordFreelancerVehicleAssigned(any(), any(), anyList())).thenReturn(Mono.empty());
        when(deliveryLifecycle.recordFreelancerVehicleAssigned(eq(unknown), any(), anyList()))
                .thenReturn(Mono.error(new IllegalStateException("delivery not found")));

        sendTwiceThenSentinel(TntTopics.VEHICLE_ASSIGNED_TO_MISSION,
                "{\"missionId\":\"" + delivery + "\",\"vehicleId\":\"V1\"}", delivery.toString(), sentinel.toString());
        verify(deliveryLifecycle, timeout(30_000)).recordFreelancerVehicleAssigned(eq(sentinel), eq("V1"), anyList());
        verify(deliveryLifecycle, times(1)).recordFreelancerVehicleAssigned(eq(delivery), eq("V1"), anyList());

        String value = "{\"missionId\":\"" + unknown + "\",\"vehicleId\":\"V1\"}";
        send(TntTopics.VEHICLE_ASSIGNED_TO_MISSION, value);
        awaitDeadLetter("tnt-delivery-vehicle-consumer", value);
        verify(deliveryLifecycle, times(ATTEMPTS)).recordFreelancerVehicleAssigned(eq(unknown), any(), anyList());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // notify-core — FreelancerOrgKafkaEventConsumer
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FreelancerOrgKafkaEventConsumer: notification sent once, failed send dead-lettered")
    void freelancerOrgNotification() throws Exception {
        String org = "org-" + UUID.randomUUID();
        String sentinel = "org-" + UUID.randomUUID();
        String failing = "org-" + UUID.randomUUID();
        when(notifications.send(any(), any(), eq(failing), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("gateway down")));

        sendTwiceThenSentinel("tnt.admin.freelancer_org.unsuspended",
                "{\"tenantId\":\"" + SYSTEM_TENANT + "\",\"orgId\":\"" + org + "\"}", org, sentinel);
        // unsuspended = PUSH + SMS: two sends per processed record
        verify(notifications, timeout(30_000).times(2)).send(any(), any(), eq(sentinel), any(), any(), any());
        verify(notifications, times(2)).send(any(), any(), eq(org), any(), any(), any());

        String value = "{\"tenantId\":\"" + SYSTEM_TENANT + "\",\"orgId\":\"" + failing + "\"}";
        send("tnt.admin.freelancer_org.unsuspended", value);
        awaitDeadLetter("tnt-notify-core", value);
        // the PUSH send fails on each attempt; the SMS send() is called when the chain is assembled
        // (`push.then(sms)`), so each attempt calls send() twice: 2 x ATTEMPTS calls = ATTEMPTS attempts
        verify(notifications, times(2 * ATTEMPTS)).send(any(), any(), eq(failing), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // go-freelancer — SubscriptionConfirmationConsumer
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SubscriptionConfirmationConsumer: processed once, failure dead-lettered")
    void gofpSubscription() throws Exception {
        UUID announcement = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        UUID freelancer = UUID.randomUUID();
        when(gofpAnnouncements.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(gofpAnnouncements.findById(failing)).thenReturn(Mono.error(new IllegalStateException("db")));

        sendTwiceThenSentinel(TntTopics.GOFP_SUBSCRIPTION_ATTEMPTS,
                "{\"announcementId\":\"" + announcement + "\",\"freelancerId\":\"" + freelancer + "\"}",
                announcement.toString(), sentinel.toString());
        verify(gofpAnnouncements, timeout(30_000)).findById(sentinel);
        verify(gofpAnnouncements, times(1)).findById(announcement);

        String value = "{\"announcementId\":\"" + failing + "\",\"freelancerId\":\"" + freelancer + "\"}";
        send(TntTopics.GOFP_SUBSCRIPTION_ATTEMPTS, value);
        awaitDeadLetter("tnt-go-freelancer-core-subscription-group", value);
        verify(gofpAnnouncements, times(ATTEMPTS)).findById(failing);
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────────

    private void send(String topic, String value) throws Exception {
        var headers = new RecordHeaders();
        headers.add("X-Yow-Event-Id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(new ProducerRecord<>(topic, null, UUID.randomUUID().toString(), value, headers))
                .get(30, TimeUnit.SECONDS);
    }

    /**
     * Sends {@code value} twice with the same key and event id, then on the same partition a distinct event
     * ({@code from} replaced by {@code to}, new event id): once the sentinel is processed, both copies were handled.
     */
    private void sendTwiceThenSentinel(String topic, String value, String from, String to) throws Exception {
        String key = UUID.randomUUID().toString();
        byte[] eventId = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 2; i++) {
            var headers = new RecordHeaders();
            headers.add("X-Yow-Event-Id", eventId);
            kafkaTemplate.send(new ProducerRecord<>(topic, null, key, value, headers)).get(30, TimeUnit.SECONDS);
        }
        var headers = new RecordHeaders();
        headers.add("X-Yow-Event-Id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(new ProducerRecord<>(topic, null, key, value.replace(from, to), headers))
                .get(30, TimeUnit.SECONDS);
    }

    private void awaitDeadLetter(String group, String value) {
        ConsumerRecord<String, String> dead = awaitRecord(TntTopics.DLQ, r -> value.equals(r.value())
                && r.headers().lastHeader(DLT_GROUP_HEADER) != null
                && group.equals(new String(r.headers().lastHeader(DLT_GROUP_HEADER).value(), StandardCharsets.UTF_8)));
        assertThat(dead.headers().lastHeader("kafka_dlt-exception-message")).as("cause kept").isNotNull();
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, Predicate<ConsumerRecord<String, String>> match) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-it-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no matching record on topic " + topic + " within 90s");
    }
}
