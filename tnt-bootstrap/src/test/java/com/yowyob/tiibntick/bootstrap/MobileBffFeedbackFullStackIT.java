package com.yowyob.tiibntick.bootstrap;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The feedback of the mobile/BFF developer of 2026-11-28 (decisions D-180 to D-187), end to end on the real
 * application: real security chains and JWT converter, real {@code @RequirePermission} aspect, real Go-Freelancer,
 * delivery-core, resource-core and geo-core services on PostgreSQL, real transactional outbox. Only the signature check
 * of the Kernel token is replaced (the decoder hands back the claims of the scenario); the Kernel URL points to a dead
 * local port, so that no call can reach the production Kernel.
 *
 * <p>Runs against the l7 containers, like {@link PlatformAdminFullStackIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@DisplayName("Mobile/BFF feedback — independent courier's vehicle, courier auto-approval, admin anywhere, relay capacity")
class MobileBffFeedbackFullStackIT {

    private static final UUID SYSTEM = SystemTenant.id();
    private static final Map<String, Jwt> TOKENS = new ConcurrentHashMap<>();
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

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
        registry.add("TNT_SYSTEM_TENANT_ID", SYSTEM::toString);
        registry.add("ELASTICSEARCH_ENABLED", () -> "false");
        registry.add("TNT_SENTIMENT_ENABLED", () -> "false");
        registry.add("JWT_AUTO_GENERATE_KEY_PAIR", () -> "true");
        registry.add("TNT_AUTH_ALLOW_ANONYMOUS", () -> "false");
        registry.add("TNT_ROLES_AOP_ENABLED", () -> "true");
        registry.add("TNT_ROLES_PROVISION_ON_STARTUP", () -> "false");
        // never the production Kernel: a dead local port
        registry.add("tnt.kernel.base-url", () -> "http://localhost:9");
        registry.add("tnt.kernel.api-key", () -> "it-none");
        registry.add("tnt.kernel.client-id", () -> "it-none");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @MockitoBean ReactiveJwtDecoder jwtDecoder;
    @Autowired WebTestClient web;
    @Autowired DatabaseClient db;
    @Autowired AddressUseCase addresses;

    @BeforeEach
    void decoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(i -> {
            Jwt jwt = TOKENS.get(i.<String>getArgument(0));
            return jwt == null ? Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("unknown"))
                    : Mono.just(jwt);
        });
    }

    private static String token(UUID subject, UUID tid, List<String> permissions) {
        String value = "it-" + UUID.randomUUID();
        TOKENS.put(value, Jwt.withTokenValue(value).header("alg", "none").subject(subject.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("tid", tid.toString()).claim("permissions", permissions).build());
        return value;
    }

    private Map<String, Object> row(String sql, UUID id) {
        return db.sql(sql).bind("id", id).fetch().one().block(Duration.ofSeconds(10));
    }

    /** A courier profile of {@code user}, logged in {@code tenant}; returns its id. */
    private UUID courier(UUID user, String tokenValue) {
        UUID profile = UUID.randomUUID();
        web.post().uri("/api/v1/gofp/freelancer-profiles").headers(h -> h.setBearerAuth(tokenValue))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("coreFreelancerId", profile.toString()))
                .exchange().expectStatus().isCreated();
        return profile;
    }

    @Test
    @DisplayName("D-181 + D-184 + D-182: an independent courier is approved at creation, saves his vehicle (no 500), "
            + "and a TNT_ADMIN logged elsewhere suspends him")
    void courierFlow() {
        UUID courierTenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String courierToken = token(user, courierTenant, List.of());

        // D-181: created APPROVED in the courier's tenant, never from the body
        UUID profile = courier(user, courierToken);
        Map<String, Object> dp = row("SELECT tenant_id, status FROM tnt_delivery_persons WHERE id = :id", profile);
        assertThat(String.valueOf(dp.get("tenant_id"))).isEqualTo(courierTenant.toString());
        assertThat(dp.get("status")).isEqualTo("APPROVED");

        // D-184: the personal vehicle of an independent courier (no organisation) — was a 500
        String plate = "IT " + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        web.put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle").headers(h -> h.setBearerAuth(courierToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "moto", "plateNumber", plate, "maxCapacityKg", 30))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).value(v -> assertThat(v.get("plateNumber")).isEqualTo(plate));
        Map<String, Object> envelope = db.sql("SELECT tenant_id, aggregate_id, kafka_partition_key, payload::text AS p "
                        + "FROM event_bus.domain_event_envelopes WHERE kafka_topic = "
                        + "'tnt.resource.freelancer.vehicle.registered' AND payload::text LIKE :plate")
                .bind("plate", "%" + plate + "%").fetch().one().block(Duration.ofSeconds(10));
        assertThat(envelope).as("one registered event, under the courier's tenant").isNotNull();
        assertThat(String.valueOf(envelope.get("tenant_id"))).isEqualTo(courierTenant.toString());
        assertThat(envelope.get("kafka_partition_key")).isEqualTo(envelope.get("aggregate_id"));
        assertThat((String) envelope.get("p")).contains("\"ownerActorId\":\"" + user + "\"");

        // D-182: someone with the gofp-admin permission in another tenant, but not TNT_ADMIN, does not find him (404,
        // not the misleading 409 of a status read as the PENDING default)
        UUID otherTenant = UUID.randomUUID();
        web.patch().uri("/api/v1/gofp/freelancer-profiles/" + profile + "/status?status=SUSPENDED")
                .headers(h -> h.setBearerAuth(token(UUID.randomUUID(), otherTenant, List.of("gofp-admin:manage"))))
                .exchange().expectStatus().isNotFound();

        // D-182: a TNT_ADMIN logged in another tenant suspends him, in the courier's own tenant
        UUID admin = UUID.randomUUID();
        db.sql("INSERT INTO tnt_platform_role_grants (id, role_code, user_id, grant_kind, granted_by, source_tenant_id, "
                        + "system_tenant_at_grant) VALUES (gen_random_uuid(), 'TNT_ADMIN', :u, 'ASSIGNED', :u, :t, :s)")
                .bind("u", admin).bind("t", otherTenant).bind("s", SYSTEM).fetch().rowsUpdated().block();
        web.patch().uri("/api/v1/gofp/freelancer-profiles/" + profile + "/status?status=SUSPENDED")
                .headers(h -> h.setBearerAuth(token(admin, otherTenant, List.of())))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).value(v -> assertThat(v.get("status")).isEqualTo("SUSPENDED"));
        assertThat(row("SELECT status FROM tnt_delivery_persons WHERE id = :id", profile).get("status"))
                .isEqualTo("SUSPENDED");
        // and the state machine read the real status there: SUSPENDED -> APPROVED is allowed
        web.patch().uri("/api/v1/gofp/freelancer-profiles/" + profile + "/status?status=APPROVED")
                .headers(h -> h.setBearerAuth(token(admin, otherTenant, List.of())))
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("D-181: the mobile app's path — freelancer profile in actor-core, then the projection filter — gives an "
            + "APPROVED delivery person in the courier's tenant")
    void mobileRegistrationIsApproved() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String t = token(user, tenant, List.of());
        Map<String, Object> created = web.post().uri("/api/v1/freelancers").headers(h -> h.setBearerAuth(t))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("serviceZoneIds", List.of()))
                .exchange().expectStatus().isCreated().expectBody(MAP).returnResult().getResponseBody();
        @SuppressWarnings("unchecked")
        UUID profile = UUID.fromString(((Map<String, Object>) created.get("data")).get("id").toString());

        // any freelancer-journey route projects the courier (GofpFreelancerProvisioningFilter)
        web.get().uri("/api/v1/gofp/freelancer-profiles/me/vehicle").headers(h -> h.setBearerAuth(t))
                .exchange().expectStatus().isOk();
        Map<String, Object> dp = row("SELECT tenant_id, status FROM tnt_delivery_persons WHERE id = :id", profile);
        assertThat(dp).as("projected delivery person").isNotNull();
        assertThat(String.valueOf(dp.get("tenant_id"))).isEqualTo(tenant.toString());
        assertThat(dp.get("status")).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("D-185: the relay's hub takes the capacity the operator declares, follows its changes, never below "
            + "the parcels present; the subscription quota is not used")
    void relayCapacityFlow() {
        UUID user = UUID.randomUUID();
        String ownerToken = token(user, SYSTEM, List.of());
        courier(user, ownerToken);
        UUID address = addresses.createAddress(AddressDTO.builder()
                        .address(Address.builder().street("Rue Joss").city("Douala").country("CM")
                                .coordinates(GeoCoordinates.of(4.05, 9.70)).build()).build())
                .block(Duration.ofSeconds(10)).getId();

        Map<String, Object> rp = web.put().uri("/api/v1/gofp/relay-point-profiles/mine")
                .headers(h -> h.setBearerAuth(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Boutique IT D-185", "physicalAccessAddressId", address.toString(),
                        "parcelCapacity", 40, "maxDeposits", 999))
                .exchange().expectStatus().isOk().expectBody(MAP).returnResult().getResponseBody();
        assertThat(rp.get("status")).isEqualTo("APPROVED");
        assertThat(rp.get("parcelCapacity")).isEqualTo(40);
        assertThat(rp.get("maxDeposits")).as("the quota stays out of the owner's hands").isNotEqualTo(999);
        UUID hub = UUID.fromString(rp.get("coreRelayPointId").toString());
        assertThat(row("SELECT capacity_slots FROM tnt_geography.relay_hubs WHERE id = :id", hub)
                .get("capacity_slots")).isEqualTo(40);

        // the operator changes it: the hub follows at once
        web.put().uri("/api/v1/gofp/relay-point-profiles/mine").headers(h -> h.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("parcelCapacity", 15))
                .exchange().expectStatus().isOk();
        assertThat(row("SELECT capacity_slots FROM tnt_geography.relay_hubs WHERE id = :id", hub)
                .get("capacity_slots")).isEqualTo(15);

        // 12 parcels present: going down to 5 is refused (409), nothing changed
        db.sql("UPDATE tnt_geography.relay_hubs SET current_occupancy = 12 WHERE id = :id").bind("id", hub)
                .fetch().rowsUpdated().block();
        web.put().uri("/api/v1/gofp/relay-point-profiles/mine").headers(h -> h.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("parcelCapacity", 5))
                .exchange().expectStatus().isEqualTo(409);
        assertThat(row("SELECT capacity_slots FROM tnt_geography.relay_hubs WHERE id = :id", hub)
                .get("capacity_slots")).isEqualTo(15);
        assertThat(row("SELECT parcel_capacity FROM gofp_relay_points WHERE core_relay_point_id = :id", hub)
                .get("parcel_capacity")).isEqualTo(15);

        // a capacity of 0 is a bad request
        web.put().uri("/api/v1/gofp/relay-point-profiles/mine").headers(h -> h.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("parcelCapacity", 0))
                .exchange().expectStatus().isBadRequest();
    }
}
