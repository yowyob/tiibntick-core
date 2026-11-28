package com.yowyob.tiibntick.bootstrap;

import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform administrators end to end (decisions D-159 to D-175) on the real application: real security chains,
 * real JWT converter, real {@code @RequirePermission} aspect and {@code @PreAuthorize}, real registry on PostgreSQL,
 * real audit topic on Kafka. Only the signature check of the Kernel token is replaced (the decoder hands back the
 * claims of the scenario), since no test may reach the production Kernel.
 *
 * <p>Runs against the l7 containers, like {@link TntApplicationFullStackIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
@EnabledIfEnvironmentVariable(named = "TNT_IT_KAFKA_BOOTSTRAP", matches = ".+")
@DisplayName("TNT_ADMIN registry end to end — any tenant, owner protected, forged claims refused")
class PlatformAdminFullStackIT {

    private static final UUID SYSTEM = SystemTenant.id();
    private static final Map<String, Jwt> TOKENS = new ConcurrentHashMap<>();
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};
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
        // the real authorization path: no dev principal, the permission aspect on
        registry.add("TNT_AUTH_ALLOW_ANONYMOUS", () -> "false");
        registry.add("TNT_ROLES_AOP_ENABLED", () -> "true");
        registry.add("TNT_ROLES_PROVISION_ON_STARTUP", () -> "false");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @MockitoBean ReactiveJwtDecoder jwtDecoder;
    // the two exits to the Kernel (no test may reach it); everything before them is real: catalog, languages, notify
    @MockitoBean com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort delivery;
    @MockitoBean com.yowyob.tiibntick.core.notify.application.port.out.IInAppInboxPort inboxPort;
    @Autowired WebTestClient web;
    @Autowired DatabaseClient db;

    // fresh accounts per run: the l7 database is kept between runs
    final UUID formerOwner = UUID.randomUUID();
    final UUID owner = UUID.randomUUID();
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final UUID stranger = UUID.randomUUID();
    final UUID otherTenant = UUID.randomUUID();
    final UUID aliceTenant = UUID.randomUUID();

    @BeforeEach
    void decoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(i -> {
            Jwt jwt = TOKENS.get(i.<String>getArgument(0));
            return jwt == null ? Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("unknown"))
                    : Mono.just(jwt);
        });
        when(delivery.deliver(any())).thenReturn(Mono.just(
                new com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.DeliveryReceipt(
                        UUID.randomUUID().toString(), "ACCEPTED")));
        when(inboxPort.send(any(), any(), any(), any(), anyMap())).thenAnswer(i -> Mono.just(
                new com.yowyob.tiibntick.core.notify.domain.model.InAppMessage(UUID.randomUUID(), i.getArgument(0),
                        i.getArgument(2), i.getArgument(3), i.getArgument(4), Instant.now(), null)));
    }

    private static String token(UUID subject, UUID tid, List<String> permissions) {
        String value = "it-" + UUID.randomUUID();
        TOKENS.put(value, Jwt.withTokenValue(value).header("alg", "none").subject(subject.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("tid", tid.toString()).claim("permissions", permissions).build());
        return value;
    }

    private WebTestClient.RequestHeadersSpec<?> get(String path, String token) {
        return web.get().uri(path).headers(h -> h.setBearerAuth(token));
    }

    private Map<String, Object> grantRow(UUID user) {
        return db.sql("SELECT grant_kind, granted_by, source_tenant_id, revoked_at, revoked_by, revoke_reason "
                        + "FROM tnt_platform_role_grants WHERE user_id = :u ORDER BY granted_at DESC LIMIT 1")
                .bind("u", user).fetch().one().block();
    }

    @Test
    void theWholeLifecycle() {
        String ownerInSystem = token(owner, SYSTEM, List.of("ROLE_OWNER", "tenant:admin"));
        // the owner reads English; Alice stored nothing (platform default: French)
        db.sql("INSERT INTO i18n.user_language_preference (user_id, language, source) VALUES (:u, 'en', 'USER')")
                .bind("u", owner).fetch().rowsUpdated().block();

        // 0. a former Kernel owner of the system tenant logged in first
        get("/api/v1/admin/platform-admins", token(formerOwner, SYSTEM, List.of("ROLE_OWNER"))).exchange()
                .expectStatus().isOk();
        assertThat(grantRow(formerOwner).get("grant_kind")).isEqualTo("SYSTEM_OWNER");

        // 1. first login of the owner in the system tenant: SYSTEM_OWNER recorded (D-161)
        get("/api/v1/admin/platform-admins", ownerInSystem).exchange().expectStatus().isOk();
        Map<String, Object> ownerRow = grantRow(owner);
        assertThat(ownerRow.get("grant_kind")).isEqualTo("SYSTEM_OWNER");
        assertThat(ownerRow.get("source_tenant_id")).isEqualTo(SYSTEM);
        // 1b. D-177: the ownership changed hands, the former owner stays TNT_ADMIN as an ordinary, revocable grant
        Map<String, Object> formerRow = grantRow(formerOwner);
        assertThat(formerRow.get("grant_kind")).isEqualTo("ASSIGNED");
        assertThat(formerRow.get("granted_by")).isEqualTo(owner);
        assertThat(formerRow.get("revoked_at")).isNull();
        assertThat(db.sql("SELECT count(*) AS n FROM tnt_platform_role_grants WHERE user_id = :u AND grant_kind = "
                        + "'SYSTEM_OWNER' AND revoke_reason LIKE 'system-tenant ownership transferred%'")
                .bind("u", formerOwner).fetch().one().block().get("n")).isEqualTo(1L);

        // 2. the owner logged in another tenant is still TNT_ADMIN and grants from there (D-159, D-164)
        String ownerElsewhere = token(owner, otherTenant, List.of());
        Map<String, Object> aliceGrant = web.post().uri("/api/v1/admin/platform-admins")
                .headers(h -> h.setBearerAuth(ownerElsewhere)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("targetUserId", alice.toString()))
                .exchange().expectStatus().isCreated().expectBody(MAP).returnResult().getResponseBody();
        assertThat(aliceGrant.get("sourceTenantId")).isEqualTo(otherTenant.toString());
        assertThat(aliceGrant.get("grantedBy")).isEqualTo(owner.toString());
        assertThat(aliceGrant.get("protectedGrant")).isEqualTo(false);

        // 2b. D-172 + maintainer directive of 2026-11-27: e-mail to Alice (French) and to the owner (English), in-app message to the owner (English)
        var mails = org.mockito.ArgumentCaptor.forClass(
                com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.OutboundMessage.class);
        verify(delivery, timeout(10_000).atLeast(2)).deliver(mails.capture());
        var toOwner = mails.getAllValues().stream().filter(m -> owner.toString().equals(m.recipientId())
                && "notify.roles.platform_admin.granted".equals(m.templateCode())
                && alice.toString().equals(m.variables().get("userId"))).findFirst().orElseThrow();
        assertThat(toOwner.channel()).isEqualTo(com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel.EMAIL);
        assertThat(toOwner.locale()).isEqualTo("en");
        assertThat(toOwner.subject()).isEqualTo("TiiBnTick — TNT_ADMIN role granted to account " + alice);
        assertThat(toOwner.body()).contains("was granted to account " + alice).contains(otherTenant.toString());
        var toAlice = mails.getAllValues().stream().filter(m -> alice.toString().equals(m.recipientId())).findFirst()
                .orElseThrow();
        assertThat(toAlice.locale()).isEqualTo("fr");
        assertThat(toAlice.subject()).isEqualTo("TiiBnTick — rôle TNT_ADMIN attribué au compte " + alice);
        var inApp = org.mockito.ArgumentCaptor.forClass(String.class);
        var inAppTitle = org.mockito.ArgumentCaptor.forClass(String.class);
        // also, in English: the ownership transfer of step 1 (D-177) and the owner's own SYSTEM_OWNER grant
        verify(inboxPort, timeout(10_000).atLeast(4)).send(eq(owner), eq("tiibntick-admin"), inAppTitle.capture(),
                inApp.capture(), anyMap());
        assertThat(inAppTitle.getAllValues()).contains(
                "TiiBnTick — TNT_ADMIN role revoked for account " + formerOwner,
                "TiiBnTick — TNT_ADMIN role granted to account " + formerOwner,
                "TiiBnTick — TNT_ADMIN role granted to account " + owner,
                "TiiBnTick — TNT_ADMIN role granted to account " + alice);
        assertThat(inApp.getAllValues()).contains("🛡️ TNT_ADMIN role granted to account " + alice + " by " + owner
                + " (source tenant " + otherTenant + ").");

        // 3. Alice, never member of the system tenant, logged in her own tenant: both kinds of checks pass (D-160)
        String aliceToken = token(alice, aliceTenant, List.of());
        get("/api/v1/admin/platform-admins", aliceToken).exchange().expectStatus().isOk();    // @RequirePermission
        get("/api/v1/admin/i18n/messages/missing", aliceToken).exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));                   // hasRole('TNT_ADMIN')

        // 3b. D-178: the administrator reads another tenant through /tenants/{id}, but does not write there
        String guard = "The tenant in the URL is not your tenant.";
        web.get().uri("/api/v1/tenants/" + otherTenant + "/deliveries/" + UUID.randomUUID())
                .headers(h -> h.setBearerAuth(aliceToken)).exchange()
                .expectBody(String.class).value(b -> assertThat(b == null ? "" : b).doesNotContain(guard));
        web.post().uri("/api/v1/tenants/" + otherTenant + "/deliveries").headers(h -> h.setBearerAuth(aliceToken))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of())
                .exchange().expectStatus().isForbidden().expectBody(String.class).value(b -> assertThat(b).contains(guard));
        web.get().uri("/api/v1/tenants/" + otherTenant + "/deliveries/" + UUID.randomUUID())
                .headers(h -> h.setBearerAuth(token(stranger, aliceTenant, List.of("delivery:read")))).exchange()
                .expectStatus().isForbidden().expectBody(String.class).value(b -> assertThat(b).contains(guard));

        // 4. forged claims make nobody an administrator (D-162)
        String forged = token(stranger, otherTenant, List.of("ROLE_TNT_ADMIN", "ROLE_TNT_ADMIN#TENANT:" + otherTenant,
                "*", "system:admin", "ROLE_OWNER"));
        get("/api/v1/admin/platform-admins", forged).exchange().expectStatus().isForbidden();
        get("/api/v1/admin/i18n/messages/missing", forged).exchange().expectStatus().isForbidden();

        // 5. the owner's grant cannot be revoked, even by an administrator (D-163); the mass revocation is the owner's
        String ownerGrantId = web.get().uri("/api/v1/admin/platform-admins").headers(h -> h.setBearerAuth(aliceToken))
                .exchange().expectBody(LIST).returnResult().getResponseBody().stream()
                .filter(g -> owner.toString().equals(g.get("userId"))).findFirst().orElseThrow()
                .get("id").toString();
        web.delete().uri("/api/v1/admin/platform-admins/" + ownerGrantId).headers(h -> h.setBearerAuth(aliceToken))
                .exchange().expectStatus().isEqualTo(409);
        web.post().uri("/api/v1/admin/platform-admins/revocations/foreign-tenant/preview")
                .headers(h -> h.setBearerAuth(aliceToken)).exchange().expectStatus().isForbidden();

        // 6. the former roles endpoint sends TNT_ADMIN to the registry (D-171), source = the caller's tenant
        web.post().uri("/api/v1/admin/roles/assignments").headers(h -> h.setBearerAuth(aliceToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("targetUserId", bob.toString(), "roleCode", "TNT_ADMIN"))
                .exchange().expectStatus().isCreated();
        Map<String, Object> bobRow = grantRow(bob);
        assertThat(bobRow.get("grant_kind")).isEqualTo("ASSIGNED");
        assertThat(bobRow.get("granted_by")).isEqualTo(alice);
        assertThat(bobRow.get("source_tenant_id")).isEqualTo(aliceTenant);
        assertThat(db.sql("SELECT count(*) AS n FROM tnt_user_role_assignments WHERE user_id = :u").bind("u", bob)
                .fetch().one().block().get("n")).isEqualTo(0L);

        // 7. mass revocation of the grants obtained from another tenant, preview then confirmation (D-165)
        Map<String, Object> preview = web.post().uri("/api/v1/admin/platform-admins/revocations/foreign-tenant/preview")
                .headers(h -> h.setBearerAuth(ownerElsewhere)).exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listed = (List<Map<String, Object>>) preview.get("grants");
        assertThat(listed).extracting(g -> g.get("userId")).contains(alice.toString(), bob.toString())
                .doesNotContain(owner.toString());
        web.post().uri("/api/v1/admin/platform-admins/revocations/foreign-tenant")
                .headers(h -> h.setBearerAuth(ownerInSystem)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("confirmationToken", preview.get("confirmationToken"), "reason", "IT D-165"))
                .exchange().expectStatus().isOk();
        Map<String, Object> aliceRow = grantRow(alice);
        assertThat(aliceRow.get("revoked_at")).isNotNull();
        assertThat(aliceRow.get("revoked_by")).isEqualTo(owner);
        assertThat(aliceRow.get("revoke_reason")).isEqualTo("IT D-165");
        assertThat(grantRow(owner).get("revoked_at")).isNull();
        verify(inboxPort, timeout(10_000)).send(eq(owner), eq("tiibntick-admin"),
                eq("TiiBnTick — TNT_ADMIN role revoked for account " + alice),
                eq("🛡️ TNT_ADMIN role of account " + alice + " revoked by " + owner + ". Reason: IT D-165"), anyMap());

        // 8. the revocation takes effect at once on this instance
        get("/api/v1/admin/platform-admins", aliceToken).exchange().expectStatus().isForbidden();
        get("/api/v1/admin/i18n/messages/missing", aliceToken).exchange().expectStatus().isForbidden();

        // 10. D-176: an agency role of the token counts only when the Core assigned it, and on that agency only
        UUID agencyTenant = UUID.randomUUID();
        UUID agency = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        db.sql("INSERT INTO tnt_roles (id, tenant_id, code, name, scope_type, permissions, system_role, editable) "
                        + "VALUES (gen_random_uuid(), :t, 'AGENCY_MANAGER', 'Agency Manager', 'AGENCY', '', true, false) "
                        + "ON CONFLICT DO NOTHING")
                .bind("t", SYSTEM).fetch().rowsUpdated().block();
        UUID roleId = (UUID) db.sql("SELECT id FROM tnt_roles WHERE tenant_id = :t AND code = 'AGENCY_MANAGER'")
                .bind("t", SYSTEM).fetch().one().block().get("id");
        db.sql("INSERT INTO tnt_user_role_assignments (id, tenant_id, user_id, role_id, scope_type, scope_id) "
                        + "VALUES (gen_random_uuid(), :t, :u, :r, 'AGENCY', :a)")
                .bind("t", agencyTenant).bind("u", manager).bind("r", roleId).bind("a", agency)
                .fetch().rowsUpdated().block();
        String otherAgency = "ROLE_AGENCY_MANAGER#AGENCY:" + UUID.randomUUID();
        List<String> claims = List.of("ROLE_AGENCY_MANAGER#AGENCY:" + agency, otherAgency);
        assertThat(authoritiesOf(manager, agencyTenant, claims))
                .contains("ROLE_AGENCY_MANAGER#AGENCY:" + agency).doesNotContain(otherAgency);
        assertThat(authoritiesOf(stranger, agencyTenant, claims))
                .noneMatch(a -> a.startsWith("ROLE_AGENCY_MANAGER"));
        assertThat(authoritiesOf(manager, UUID.randomUUID(), claims)).as("another tenant of the same account")
                .noneMatch(a -> a.startsWith("ROLE_AGENCY_MANAGER"));

        // 9. audit events on Kafka (D-172)
        List<String> events = auditEventsOf(alice);
        assertThat(events).anyMatch(e -> e.contains("\"type\":\"GRANTED\""))
                .anyMatch(e -> e.contains("\"type\":\"REVOKED\"") && e.contains("IT D-165"));
    }

    @Autowired org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter converter;

    private List<String> authoritiesOf(UUID subject, UUID tid, List<String> permissions) {
        String value = token(subject, tid, permissions);
        return converter.convert(TOKENS.get(value)).block().getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
    }

    private static List<String> auditEventsOf(UUID user) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("TNT_IT_KAFKA_BOOTSTRAP"));
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-platform-admin-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<String> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
            consumer.subscribe(List.of(TntTopics.ROLES_PLATFORM_ADMIN_CHANGED));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline && found.size() < 2) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (user.toString().equals(r.key())) found.add(r.value());
                }
            }
        }
        return found;
    }
}
