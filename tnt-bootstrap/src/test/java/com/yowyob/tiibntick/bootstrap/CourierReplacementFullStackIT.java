package com.yowyob.tiibntick.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.kafka.TntEventSchemas;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICourierLegRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IDebtRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentMirrorRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CompleteDeliveryCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.ConfirmPickupCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.StartTransitCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.incident.port.outbound.IDeliveryStatusPort;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * QO-11 / D-153 on the whole application: a courier replaced in transit (hand-over at a known point) is paid by the
 * compensation fund at the prorata of the distance when the escrow is released, and the first courier owes the fund.
 *
 * <p>Real PostGIS (Liquibase applied), Kafka and Redis, real delivery services, outbox, Kafka consumers and wallet
 * services; only the Kernel is replaced, by a local HTTP stub that answers the payment routes the way the Kernel does
 * ({@code {"success": true, "data": …}}) and records every call. Same variables as {@link TntApplicationFullStackIT}:
 * <pre>
 *   TNT_IT_KAFKA_BOOTSTRAP=localhost:19793 TNT_IT_DB_PORT=15444 TNT_IT_DB_PASSWORD=… TNT_IT_REDIS_PORT=16390 \
 *   mvn -o verify -Pintegration-tests -pl tnt-bootstrap -am -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false \
 *       -Dit.test=CourierReplacementFullStackIT -Dfailsafe.failIfNoSpecifiedTests=false
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@DisplayName("QO-11 / D-153 end to end: replacement courier paid by the fund at the prorata of the distance")
class CourierReplacementFullStackIT {

    private static final String SYSTEM_TENANT = "d3b07384-d9a0-4c9c-b7d1-5f2a1b6c0e91";
    private static final ObjectMapper MAPPER = TntEventJson.mapper();
    private static final UUID FUND_WALLET = UUID.fromString("f0d00000-0000-0000-0000-00000000f0d0");
    private static final Queue<String> KERNEL_CALLS = new ConcurrentLinkedQueue<>();
    private static final HttpServer KERNEL = startKernelStub();

    // Yaoundé: pickup at Bastos, hand-over at the Poste centrale, delivery at Mvan
    private static final GeoCoordinates PICKUP = new GeoCoordinates(3.8840, 11.5130);
    private static final GeoCoordinates HANDOVER = new GeoCoordinates(3.8667, 11.5167);
    private static final GeoCoordinates DESTINATION = new GeoCoordinates(3.8160, 11.5200);

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
        registry.add("TNT_ROLES_AOP_ENABLED", () -> "false");
        // tnt.kernel.* is only bound to TNT_KERNEL_* in the prod profile of application.yml: outside it every Kernel
        // client falls back to the production URL, so the property itself is overridden here (never reach the Kernel)
        registry.add("tnt.kernel.base-url", () -> "http://localhost:" + KERNEL.getAddress().getPort());
        registry.add("tnt.kernel.api-key", () -> "it-stub-key");
        registry.add("tnt.kernel.client-id", () -> "it-stub-client");
        registry.add("yow.event.outbox.relay.poll-interval-ms", () -> "1000");
        registry.add("yow.event.outbox.relay.initial-delay", () -> "1s");
        registry.add("yow.event.outbox.relay.pending-grace", () -> "5s");
    }

    @AfterAll
    static void stopKernel() {
        KERNEL.stop(0);
    }

    @Autowired DeliveryRepository deliveries;
    @Autowired DeliveryTransitionExecutor deliveryTransitions;
    @Autowired DeliveryLifecycleUseCase deliveryLifecycle;
    @Autowired IDeliveryStatusPort deliveryStatusPort;
    @Autowired IPaymentMirrorRepository paymentMirrors;
    @Autowired ICourierLegRepository courierLegs;
    @Autowired IDebtRepository debts;

    @Test
    @DisplayName("hand-over in transit → legs in the event and in billing.wallet_courier_legs; delivered → escrow released, "
            + "the fund pays the replacement REPLACEMENT:<id>:1, the first courier's debt opens; nothing dead-lettered")
    void replacementCourierPaidByTheFund() throws Exception {
        UUID tenant = UUID.fromString(SYSTEM_TENANT);
        UUID sender = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        Duration wait = Duration.ofSeconds(30);

        Parcel parcel = Parcel.create(new PackageSpecification(2, 20, 20, 20, false, false, "Box"), tenant, sender,
                TntEventActor.of(TntEventActor.Type.SENDER, sender));
        Delivery delivery = Delivery.create(tenant, null, sender, parcel,
                new DeliveryAddress("Rue 1", null, "Bastos", "Yaoundé", "CM", PICKUP),
                new DeliveryAddress("Rue 2", null, "Mvan", "Yaoundé", "CM", DESTINATION),
                new RecipientInfo("Awa Ngono", "+237699000222", null), DeliveryUrgency.STANDARD, null, null);
        UUID id = delivery.getId();

        // the price is escrowed for the first courier (lot L5): 3 000 XAF, platform 150, courier part 2 850
        paymentMirrors.saveIntent(new PaymentIntent(UUID.randomUUID(), tenant, id, null, null,
                PaymentIntent.Mode.ESCROW, new BigDecimal("3000"), "XAF", new EscrowShares(new BigDecimal("3000"),
                new BigDecimal("150"), List.of(), new BigDecimal("2850")), sender, UUID.randomUUID(), first,
                walletOf(first), UUID.randomUUID(), null, Instant.now(), Instant.now()), true).block(wait);
        UUID kernelEscrow = UUID.randomUUID();
        paymentMirrors.saveEscrow(new EscrowMirror(UUID.randomUUID(), tenant, id, EscrowMirror.DELIVERY, kernelEscrow,
                EscrowMirror.Status.HELD, new BigDecimal("3000"), "XAF", UUID.randomUUID(), null, Instant.now(),
                Instant.now()), true).block(wait);

        // assigned, picked up, in transit with the first courier
        Delivery created = deliveries.save(delivery).block(wait);
        created.assignDeliveryPerson(first, null, 2.0, null);
        deliveryTransitions.saveAndPublish(created).block(wait);
        deliveryLifecycle.confirmPickup(new ConfirmPickupCommand(tenant, id, first)).block(wait);
        deliveryLifecycle.startTransit(new StartTransitCommand(tenant, id, first, null)).block(wait);

        // what the incident module does on a blocking incident followed by a replacement (rows D13, D15, D17, D18)
        UUID incident = UUID.randomUUID();
        deliveryStatusPort.markIncidentReported(id, incident).block(wait);
        deliveryStatusPort.pauseDelivery(id, incident).block(wait);
        deliveryStatusPort.startHandover(id).block(wait);
        deliveryStatusPort.resumeAfterHandover(id, replacement, null, HANDOVER.latitude(), HANDOVER.longitude())
                .block(wait);
        assertThat(deliveries.findById(tenant, id).block(wait).getDeliveryPersonId()).isEqualTo(replacement);

        deliveryLifecycle.completeDelivery(new CompleteDeliveryCommand(tenant, id, replacement, null, null, null, null,
                null)).block(wait);
        assertThat(deliveries.findById(tenant, id).block(wait).getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        BigDecimal legKm = km(PICKUP.haversineDistanceTo(HANDOVER));
        BigDecimal remainingKm = km(HANDOVER.haversineDistanceTo(DESTINATION));
        BigDecimal expected = new BigDecimal("2850").multiply(remainingKm)
                .divide(legKm.add(remainingKm), 0, RoundingMode.HALF_UP);

        // the delivery event of the hand-over carries the legs, schema-valid, on tnt.delivery.events
        ConsumerRecord<String, String> handover = awaitRecord(TntTopics.DELIVERY_EVENTS,
                r -> id.toString().equals(r.key()) && r.value().contains("replacementCourierId"));
        JsonNode details = MAPPER.readTree(handover.value()).path("details");
        assertThat(validate(handover.value())).isEmpty();
        assertThat(details.path("replacedCourierId").asText()).isEqualTo(first.toString());
        assertThat(details.path("replacementCourierId").asText()).isEqualTo(replacement.toString());
        assertThat(details.path("replacedLegKm").decimalValue()).isEqualByComparingTo(legKm);
        assertThat(details.path("remainingKm").decimalValue()).isEqualByComparingTo(remainingKm);

        // the real wallet consumer recorded the legs, released the escrow, paid the replacement and opened the debt
        String reference = "REPLACEMENT:" + id + ":1";
        await().atMost(90, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourierLeg> legs = courierLegs.byDelivery(id).collectList().block(wait);
            assertThat(legs).singleElement().satisfies(l -> {
                assertThat(l.replacedCourierId()).isEqualTo(first);
                assertThat(l.replacementCourierId()).isEqualTo(replacement);
                assertThat(l.replacedLegKm()).isEqualByComparingTo(legKm);
                assertThat(l.remainingKm()).isEqualByComparingTo(remainingKm);
            });
            assertThat(paymentMirrors.findEscrow(id, EscrowMirror.DELIVERY).block(wait).status())
                    .isEqualTo(EscrowMirror.Status.RELEASED);
            WalletDebt debt = debts.findByReference(reference).block(wait);
            assertThat(debt).isNotNull();
            assertThat(debt.debtorId()).isEqualTo(first);
            assertThat(debt.reason()).isEqualTo(WalletDebt.Reason.COURIER_REPLACEMENT);
            assertThat(debt.amount()).isEqualByComparingTo(expected);
            assertThat(debt.status()).isEqualTo(WalletDebt.Status.OPEN);
        });
        assertThat(KERNEL_CALLS).anyMatch(c -> c.equals("POST /api/payments/escrows/" + kernelEscrow + "/release"));
        List<String> compensations = KERNEL_CALLS.stream().filter(c -> c.startsWith("POST /api/payments/compensations"))
                .toList();
        assertThat(compensations).singleElement().satisfies(c -> {
            assertThat(c).contains("\"reference\":\"" + reference + "\"");
            assertThat(c).contains("\"beneficiaryWalletId\":\"" + walletOf(replacement) + "\"");
            assertThat(c).contains("\"amount\":" + expected.toPlainString());
        });

        // the fund's payment is published for accounting with its own reason
        ConsumerRecord<String, String> paid = awaitRecord(TntTopics.BILLING_COMPENSATION_PAID,
                r -> r.value().contains(id.toString()));
        assertThat(paid.value()).contains("COURIER_REPLACEMENT");

        // no consumer dead-lettered an event of this delivery
        List<ConsumerRecord<String, String>> dead = readFor(TntTopics.DLQ, Duration.ofSeconds(15),
                r -> id.toString().equals(r.key()));
        assertThat(dead).isEmpty();
    }

    // ── Kernel stub ──────────────────────────────────────────────────────────────────────────

    private static UUID walletOf(UUID owner) {
        return UUID.nameUUIDFromBytes(("wallet-" + owner).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpServer startKernelStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", CourierReplacementFullStackIT::kernel);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void kernel(HttpExchange x) throws IOException {
        String method = x.getRequestMethod();
        String path = x.getRequestURI().getPath();
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        KERNEL_CALLS.add(method + " " + path + (body.isBlank() ? "" : " " + body));
        String now = Instant.now().toString();
        String data = null;
        String[] p = path.split("/");
        if ("GET".equals(method) && path.startsWith("/api/payments/wallets/owner/")) {
            UUID owner = UUID.fromString(p[p.length - 1]);
            data = "{\"id\":\"" + walletOf(owner) + "\",\"ownerId\":\"" + owner + "\",\"ownerType\":\"USER\","
                    + "\"balance\":0}";
        } else if ("GET".equals(method) && path.equals("/api/payments/compensation-fund")) {
            data = "{\"walletId\":\"" + FUND_WALLET + "\",\"balance\":1000000}";
        } else if ("POST".equals(method) && path.equals("/api/payments/compensations")) {
            JsonNode req = MAPPER.readTree(body);
            data = "{\"id\":\"" + UUID.randomUUID() + "\",\"reference\":\"" + req.path("reference").asText()
                    + "\",\"fundWalletId\":\"" + FUND_WALLET + "\",\"beneficiaryWalletId\":\""
                    + req.path("beneficiaryWalletId").asText() + "\",\"amount\":" + req.path("amount").asText()
                    + ",\"createdAt\":\"" + now + "\"}";
        } else if ("POST".equals(method) && path.matches("/api/payments/escrows/[0-9a-f-]{36}/release")) {
            data = "{\"id\":\"" + p[4] + "\",\"subjectType\":\"DELIVERY\",\"amount\":3000,\"currency\":\"XAF\","
                    + "\"status\":\"RELEASED\",\"shares\":[],\"createdAt\":\"" + now + "\",\"settledAt\":\"" + now
                    + "\"}";
        } else if ("POST".equals(method) && path.equals("/api/payments/cash-collections")) {
            JsonNode req = MAPPER.readTree(body);
            data = collection(UUID.randomUUID(), req, "OPEN", now);
        } else if ("POST".equals(method) && path.matches("/api/payments/cash-collections/[0-9a-f-]{36}/collected")) {
            data = "{\"id\":\"" + p[4] + "\",\"subjectType\":\"DEBT\",\"status\":\"COLLECTED\",\"currency\":\"XAF\","
                    + "\"shares\":[],\"createdAt\":\"" + now + "\"}";
        }
        byte[] out = (data == null ? "{\"success\":false,\"message\":\"not stubbed\"}"
                : "{\"success\":true,\"data\":" + data + "}").getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(data == null ? 404 : 200, out.length);
        try (OutputStream os = x.getResponseBody()) {
            os.write(out);
        }
    }

    private static String collection(UUID id, JsonNode req, String status, String now) {
        return "{\"id\":\"" + id + "\",\"subjectType\":\"" + req.path("subjectType").asText() + "\",\"subjectId\":\""
                + req.path("subjectId").asText() + "\",\"courierWalletId\":\"" + req.path("courierWalletId").asText()
                + "\",\"expectedAmount\":" + req.path("expectedAmount").asText("0") + ",\"currency\":\"XAF\","
                + "\"status\":\"" + status + "\",\"shares\":[],\"createdAt\":\"" + now + "\"}";
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static BigDecimal km(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** The envelope schema (which references the payload of the aggregate), formats asserted. */
    private static List<String> validate(String body) throws Exception {
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                CourierReplacementFullStackIT.class.getClassLoader().getResourceAsStream(TntEventSchemas.ENVELOPE),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        return schema.validate(MAPPER.readTree(body)).stream().map(ValidationMessage::getMessage).toList();
    }

    private static Properties readerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("TNT_IT_KAFKA_BOOTSTRAP"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-it-qo11-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    private static ConsumerRecord<String, String> awaitRecord(String topic,
                                                              Predicate<ConsumerRecord<String, String>> match) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(readerProps())) {
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
        throw new AssertionError("No matching record on " + topic + " within 90 s");
    }

    private static List<ConsumerRecord<String, String>> readFor(String topic, Duration window,
                                                               Predicate<ConsumerRecord<String, String>> match) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(readerProps())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + window.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        out.add(record);
                    }
                }
            }
        }
        return out;
    }
}
