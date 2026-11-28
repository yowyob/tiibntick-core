package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter.DeliveryThresholdsRepositoryAdapter;
import com.yowyob.tiibntick.core.administration.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import com.yowyob.tiibntick.core.administration.application.service.DeliveryThresholdsService;
import com.yowyob.tiibntick.core.administration.support.PostgisTestDatabase;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The threshold routes end to end on PostGIS: real controllers behind the real {@code @RequirePermission} aspect and
 * permission evaluator (authorities from {@code X-Test-Perms}, roles expanded by the real {@code TntRole} registry),
 * real service and R2DBC repository on the migrated schema. Faked: the agency lookup (a set of agency organization
 * ids) and the event publisher (records the events).
 */
@Tag("integration")
@DisplayName("Delivery thresholds — HTTP routes, permissions and organization scope on PostGIS")
class DeliveryThresholdsHttpIntegrationTest {

    private static final UUID AGENCY_ORG = UUID.randomUUID();
    private static final UUID OTHER_AGENCY_ORG = UUID.randomUUID();
    private static final UUID PLAIN_ORG = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final String PLATFORM = "/api/v1/admin/settings/delivery-thresholds";

    static PostgisTestDatabase db;
    static WebTestClient web;
    static final List<Map<String, Object>> EVENTS = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void start() throws Exception {
        db = PostgisTestDatabase.start();
        db.migrate();
        JsonMapper json = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        Set<UUID> agencies = Set.of(AGENCY_ORG, OTHER_AGENCY_ORG);
        AgencyOrganizationPort agencyPort = id -> Mono.just(agencies.contains(id));
        TntAdministrationEventPublisher events = new TntAdministrationEventPublisher() {
            @Override
            public Mono<Void> publish(UUID tenantId, String eventType, String module, UUID aggregateId,
                                      Map<String, Object> payload) {
                EVENTS.add(Map.of("tenantId", tenantId, "eventType", eventType, "aggregateId", aggregateId,
                        "payload", payload));
                return Mono.empty();
            }

            @Override
            public Mono<Void> publish(String topic, UUID tenantId, Map<String, Object> payload) {
                return Mono.empty();
            }
        };
        DeliveryThresholdsService service = new DeliveryThresholdsService(
                new DeliveryThresholdsRepositoryAdapter(DatabaseClient.create(db.connectionFactory())), agencyPort,
                events);
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(mock(ReactivePermissionResolver.class),
                new TntRoleDefinitionRegistry());

        web = WebTestClient.bindToController(
                        withPermissions(new PlatformDeliveryThresholdsController(service), evaluator),
                        withPermissions(new OrganizationDeliveryThresholdsController(service, service,
                                new OrganizationScopeGuard(evaluator)), evaluator))
                .httpMessageCodecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(json));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(json));
                })
                .controllerAdvice(new DeliveryThresholdsExceptionHandler(), new TntRoleExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new HeaderUserResolver()))
                .webFilter((exchange, chain) -> {
                    String perms = exchange.getRequest().getHeaders().getFirst("X-Test-Perms");
                    if (perms == null) {
                        return chain.filter(exchange);
                    }
                    var auth = new TestingAuthenticationToken("user", "n/a", Arrays.stream(perms.split(","))
                            .filter(p -> !p.isBlank()).map(SimpleGrantedAuthority::new).toList());
                    auth.setAuthenticated(true);
                    return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                })
                .build();
    }

    @AfterAll
    static void stop() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> T withPermissions(T target, TntPermissionEvaluator evaluator) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        return (T) factory.getProxy();
    }

    /** {@code @CurrentUser} from test headers: X-Test-User, X-Test-Org. */
    static final class HeaderUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                           ServerWebExchange exchange) {
            var h = exchange.getRequest().getHeaders();
            UUID user = h.getFirst("X-Test-User") == null ? UUID.randomUUID() : UUID.fromString(h.getFirst("X-Test-User"));
            UUID org = h.getFirst("X-Test-Org") == null ? null : UUID.fromString(h.getFirst("X-Test-Org"));
            return Mono.just(new TntUserIdentity(user, TENANT, user, org, null, Set.of(), false));
        }
    }

    private static WebTestClient.RequestHeadersSpec<?> as(WebTestClient.RequestHeadersSpec<?> spec, String perms,
                                                           UUID org) {
        if (perms != null) {
            spec = spec.header("X-Test-Perms", perms);
        }
        if (org != null) {
            spec = spec.header("X-Test-Org", org.toString());
        }
        return spec;
    }

    private static String platformBody(int attempts, long version) {
        return "{\"maxDeliveryAttempts\":" + attempts + ",\"relayStorageDays\":7,\"relayStorageReminderDaysBefore\":1,"
                + "\"maxFailedReturns\":3,\"pickupLateGraceMinutes\":15,\"deliveryLateGraceMinutes\":0,"
                + "\"deliveryLateFallbackHoursStandard\":48,\"deliveryLateFallbackHoursExpress\":24,"
                + "\"deliveryLateFallbackHoursSameDay\":8,\"etaExceededGraceMinutes\":30,\"gpsSignalLostMinutes\":10,"
                + "\"prolongedStopMinutes\":20,\"independentRelayStorageMinDays\":2,"
                + "\"independentRelayStorageMaxDays\":14,\"version\":" + version + "}";
    }

    // ── platform ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("platform: 403 without tnt:platform:admin (even for an agency manager), 200 for the platform admin")
    void platformPermission() {
        as(web.get().uri(PLATFORM), "", null).exchange().expectStatus().isForbidden();
        as(web.get().uri(PLATFORM), "ROLE_AGENCY_MANAGER,settings:write,admin:settings", AGENCY_ORG).exchange()
                .expectStatus().isForbidden();
        as(web.put().uri(PLATFORM).contentType(MediaType.APPLICATION_JSON).bodyValue(platformBody(4, 0)),
                "ROLE_ORG_ADMIN", null).exchange().expectStatus().isForbidden();
        as(web.get().uri(PLATFORM), "tnt:platform:admin", null).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.maxDeliveryAttempts").isNumber();
        as(web.get().uri(PLATFORM), "ROLE_TNT_ADMIN", null).exchange().expectStatus().isOk();
        assertThat(EVENTS).isEmpty();
    }

    @Test
    @DisplayName("platform: PUT stores and publishes; stale version → 409; out of range → 422; missing field → 400")
    void platformUpdate() {
        // D-130: independent relay bounds 3..30 days out of the box (the only test changing the platform row)
        as(web.get().uri(PLATFORM), "tnt:platform:admin", null).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.independentRelayStorageMinDays").isEqualTo(3)
                .jsonPath("$.independentRelayStorageMaxDays").isEqualTo(30);
        long version = as(web.get().uri(PLATFORM), "tnt:platform:admin", null).exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody().get("version") instanceof Number n
                ? n.longValue() : -1;

        as(web.put().uri(PLATFORM).contentType(MediaType.APPLICATION_JSON).bodyValue(platformBody(4, version)),
                "tnt:platform:admin", null).exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.maxDeliveryAttempts").isEqualTo(4)
                .jsonPath("$.independentRelayStorageMinDays").isEqualTo(2)
                .jsonPath("$.independentRelayStorageMaxDays").isEqualTo(14)
                .jsonPath("$.version").isEqualTo((int) version + 1);
        assertThat(EVENTS).singleElement().satisfies(e -> {
            assertThat(e.get("eventType")).isEqualTo("DeliveryThresholdsChanged");
            assertThat(e.get("tenantId")).isEqualTo(TENANT);
        });

        as(web.put().uri(PLATFORM).contentType(MediaType.APPLICATION_JSON).bodyValue(platformBody(5, version)),
                "tnt:platform:admin", null).exchange().expectStatus().value(s -> assertThat(s).isEqualTo(409))
                .expectBody().jsonPath("$.code").isEqualTo("DELIVERY_THRESHOLDS_VERSION_CONFLICT");
        as(web.put().uri(PLATFORM).contentType(MediaType.APPLICATION_JSON).bodyValue(platformBody(11, version + 1)),
                "tnt:platform:admin", null).exchange().expectStatus().value(s -> assertThat(s).isEqualTo(422))
                .expectBody().jsonPath("$.code").isEqualTo("DELIVERY_THRESHOLDS_INVALID")
                .jsonPath("$.violations[0].field").isEqualTo("maxDeliveryAttempts");
        as(web.put().uri(PLATFORM).contentType(MediaType.APPLICATION_JSON).bodyValue("{\"maxDeliveryAttempts\":3}"),
                "tnt:platform:admin", null).exchange().expectStatus().isBadRequest();
        // the bounds are required (no "unset" any more, D-130)
        as(web.put().uri(PLATFORM).contentType(MediaType.APPLICATION_JSON).bodyValue(platformBody(4, version + 1)
                        .replace("\"independentRelayStorageMinDays\":2,", "")),
                "tnt:platform:admin", null).exchange().expectStatus().isBadRequest();
        assertThat(EVENTS).hasSize(1);
    }

    // ── organizations ────────────────────────────────────────────────────────────────────────

    private static String org(UUID id) {
        return "/api/v1/organizations/" + id + "/settings/delivery-thresholds";
    }

    @Test
    @DisplayName("organization: the agency manager overrides his agency, sees the source, removes it; events published")
    void agencyManagerLifecycle() {
        String manager = "ROLE_AGENCY_MANAGER";
        as(web.get().uri(org(AGENCY_ORG)), manager, AGENCY_ORG).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.version").doesNotExist().jsonPath("$.maxDeliveryAttempts").doesNotExist();

        as(web.put().uri(org(AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxDeliveryAttempts\":5,\"relayStorageDays\":10,\"pickupLateGraceMinutes\":30}"),
                manager, AGENCY_ORG).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.maxDeliveryAttempts").isEqualTo(5).jsonPath("$.version").isEqualTo(0)
                .jsonPath("$.maxFailedReturns").doesNotExist();

        as(web.get().uri(org(AGENCY_ORG) + "/effective"), manager, AGENCY_ORG).exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agencyOrganization").isEqualTo(true)
                .jsonPath("$.maxDeliveryAttempts.value").isEqualTo(5)
                .jsonPath("$.maxDeliveryAttempts.source").isEqualTo("ORGANIZATION")
                .jsonPath("$.relayStorageDays.source").isEqualTo("ORGANIZATION")
                .jsonPath("$.pickupLateGraceMinutes.value").isEqualTo(30)
                .jsonPath("$.maxFailedReturns.value").isEqualTo(3)
                .jsonPath("$.maxFailedReturns.source").isEqualTo("PLATFORM")
                .jsonPath("$.gpsSignalLostMinutes.source").isEqualTo("PLATFORM");

        // update needs the version read; stale → 409
        as(web.put().uri(org(AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxDeliveryAttempts\":6}"), manager, AGENCY_ORG).exchange()
                .expectStatus().value(s -> assertThat(s).isEqualTo(409));
        as(web.put().uri(org(AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxDeliveryAttempts\":6,\"version\":0}"), manager, AGENCY_ORG).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.version").isEqualTo(1)
                .jsonPath("$.relayStorageDays").doesNotExist();

        as(web.delete().uri(org(AGENCY_ORG)), manager, AGENCY_ORG).exchange().expectStatus().isNoContent();
        as(web.delete().uri(org(AGENCY_ORG)), manager, AGENCY_ORG).exchange().expectStatus().isNotFound();
        as(web.get().uri(org(AGENCY_ORG) + "/effective"), manager, AGENCY_ORG).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.maxDeliveryAttempts.source").isEqualTo("PLATFORM");

        List<Object> changes = new ArrayList<>();
        EVENTS.forEach(e -> changes.add(((Map<?, ?>) e.get("payload")).get("change")));
        assertThat(changes).containsExactly("UPDATED", "UPDATED", "DELETED");
        assertThat(EVENTS).allSatisfy(e -> assertThat(e.get("aggregateId")).isEqualTo(AGENCY_ORG));
    }

    @Test
    @DisplayName("organization: a member granted the write permission by the manager may change it; read-only members may not")
    void delegatedPermission() {
        // a custom agency role carrying the permission (granted by the manager), scoped to the agency
        as(web.put().uri(org(OTHER_AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxFailedReturns\":2}"), "organization:delivery-settings:write", OTHER_AGENCY_ORG)
                .exchange().expectStatus().isOk();
        // a branch manager (read only by default): can read the effective values, cannot change them
        as(web.get().uri(org(OTHER_AGENCY_ORG) + "/effective"), "ROLE_BRANCH_MANAGER", OTHER_AGENCY_ORG).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.maxFailedReturns.value").isEqualTo(2);
        as(web.put().uri(org(OTHER_AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxFailedReturns\":4,\"version\":0}"), "ROLE_BRANCH_MANAGER", OTHER_AGENCY_ORG)
                .exchange().expectStatus().isForbidden();
        as(web.get().uri(org(OTHER_AGENCY_ORG)), "ROLE_BRANCH_MANAGER", OTHER_AGENCY_ORG).exchange()
                .expectStatus().isForbidden();
        // clients and freelancers: nothing
        as(web.get().uri(org(OTHER_AGENCY_ORG) + "/effective"), "ROLE_CLIENT,ROLE_FREELANCER", OTHER_AGENCY_ORG)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    @DisplayName("organization scope: another organization's settings → 403; the platform admin may act on any")
    void organizationScope() {
        as(web.get().uri(org(OTHER_AGENCY_ORG) + "/effective"), "ROLE_AGENCY_MANAGER", AGENCY_ORG).exchange()
                .expectStatus().isForbidden();
        as(web.put().uri(org(OTHER_AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"gpsSignalLostMinutes\":15}"), "ROLE_AGENCY_MANAGER", AGENCY_ORG).exchange()
                .expectStatus().isForbidden();
        as(web.get().uri(org(OTHER_AGENCY_ORG) + "/effective"), "ROLE_AGENCY_MANAGER", null).exchange()
                .expectStatus().isForbidden();
        as(web.get().uri(org(OTHER_AGENCY_ORG) + "/effective"), "ROLE_TNT_ADMIN", null).exchange()
                .expectStatus().isOk();
        as(web.get().uri(PLATFORM + "/organizations"), "ROLE_TNT_ADMIN", null).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$").isArray();
        assertThat(EVENTS).isEmpty();
    }

    @Test
    @DisplayName("organization: not an Agency → 422 ORGANIZATION_NOT_AGENCY; empty body → 422; effective = platform")
    void nonAgency() {
        as(web.put().uri(org(PLAIN_ORG)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxDeliveryAttempts\":5}"), "ROLE_ORG_ADMIN", PLAIN_ORG).exchange()
                .expectStatus().value(s -> assertThat(s).isEqualTo(422)).expectBody().jsonPath("$.code").isEqualTo("ORGANIZATION_NOT_AGENCY");
        as(web.put().uri(org(AGENCY_ORG)).contentType(MediaType.APPLICATION_JSON).bodyValue("{}"),
                "ROLE_ORG_ADMIN", AGENCY_ORG).exchange()
                .expectStatus().value(s -> assertThat(s).isEqualTo(422)).expectBody().jsonPath("$.code")
                .isEqualTo("DELIVERY_THRESHOLDS_EMPTY_OVERRIDE");
        as(web.get().uri(org(PLAIN_ORG) + "/effective"), "ROLE_ORG_ADMIN", PLAIN_ORG).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.agencyOrganization").isEqualTo(false)
                .jsonPath("$.maxDeliveryAttempts.source").isEqualTo("PLATFORM");
        assertThat(EVENTS).isEmpty();
    }
}
