package com.yowyob.tiibntick.bootstrap.gofp;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.ClientProfileR2dbcRepositoryAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.FreelancerR2dbcRepositoryAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository.ClientProfileSpringDataRepository;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository.FreelancerSpringDataRepository;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.application.service.ActorRatingService;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.EvaluationController;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.GofpClientController;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.GofpClientRepositoryAdapter;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpClientR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.EvaluationApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpClientService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.SentimentAnalysisUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.tp.adapter.in.kafka.DeliveryEventConsumer;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.LoyaltyAccountRepositoryAdapter;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.ThirdPartyRatingRepositoryAdapter;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.TntClientProfileRepositoryAdapter;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.mapper.TntTpPersistenceMapper;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.LoyaltyAccountR2dbcRepository;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.ThirdPartyRatingR2dbcRepository;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.r2dbc.TntClientProfileR2dbcRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import com.yowyob.tiibntick.core.tp.application.service.RatingService;
import com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L3, criterion 5 — end to end on a real database: Go-Freelancer evaluations become core ratings, the profile
 * average follows, completed deliveries credit the loyalty account once each, and the Go client read shows the
 * core average and tier.
 *
 * <p>Real: the Go controllers ({@code POST /api/v1/evaluations}, {@code GET /api/v1/gofp/clients/{id}}) and services,
 * tp-core {@code RatingService} / {@code LoyaltyService} / {@code DeliveryEventConsumer}, actor-core
 * {@code ActorRatingService}, every R2DBC repository and adapter on PostGIS (schema from the real
 * {@code tnt-core-master.yaml}), the {@code @RequirePermission} aspect. Faked: the event publishers, the tp-core
 * client profile service (no tp profile for a Go client) and the relay point repository.
 */
@Testcontainers
@Tag("integration")
class EvaluationLoyaltyEndToEndIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("postgres")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLIENT_ACTOR = UUID.randomUUID();
    private static final UUID COURIER_1 = UUID.randomUUID();
    private static final UUID COURIER_2 = UUID.randomUUID();
    private static final List<String> PERMISSIONS = List.of("rating:create", "rating:read", "loyalty:read");

    private static ClientProfile clientProfile;
    private static FreelancerProfile courier1Profile;
    private static WebTestClient web;
    private static DeliveryEventConsumer missionCompleted;
    private static JsonMapper json;

    @BeforeAll
    static void startStack() throws Exception {
        try (Connection conn = jdbc()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibase = new Liquibase("db/changelog/tnt-core-master.yaml", com.yowyob.tiibntick.bootstrap.config.BootstrapChangelogs.accessor(),
                    database);
            liquibase.update(new Contexts(), new LabelExpression());
        }

        ConnectionFactory cf = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getMappedPort(5432))
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build());
        R2dbcEntityTemplate template = new R2dbcEntityTemplate(cf);
        R2dbcRepositoryFactory repositories = new R2dbcRepositoryFactory(template);
        json = JsonMapper.builder().findAndAddModules().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).build();

        // actor-core
        var clientProfiles = new ClientProfileR2dbcRepositoryAdapter(
                repositories.getRepository(ClientProfileSpringDataRepository.class), template);
        var freelancerProfiles = new FreelancerR2dbcRepositoryAdapter(
                repositories.getRepository(FreelancerSpringDataRepository.class), template);
        IRateActorUseCase actorRating = withPermissions(new ActorRatingService(mock(IDelivererRepository.class),
                freelancerProfiles, mock(IRelayOperatorRepository.class), monoMock(IActorEventPublisher.class),
                clientProfiles));

        // tp-core
        var mapper = new TntTpPersistenceMapper(json);
        TntClientProfileService tpProfiles = monoMock(TntClientProfileService.class);
        RatingService ratings = withPermissions(new RatingService(
                new ThirdPartyRatingRepositoryAdapter(repositories.getRepository(ThirdPartyRatingR2dbcRepository.class), mapper),
                new TntClientProfileRepositoryAdapter(repositories.getRepository(TntClientProfileR2dbcRepository.class), mapper),
                monoMock(TntTpEventPublisher.class)));
        LoyaltyService loyalty = withPermissions(new LoyaltyService(
                new LoyaltyAccountRepositoryAdapter(repositories.getRepository(LoyaltyAccountR2dbcRepository.class), mapper),
                tpProfiles, monoMock(TntTpEventPublisher.class)));
        missionCompleted = new DeliveryEventConsumer(loyalty, tpProfiles, json);

        // Go-Freelancer
        TenantContextHolder tenants = mock(TenantContextHolder.class);
        when(tenants.currentTenantId()).thenReturn(Mono.just(TENANT));
        GofpRelayPointRepository relayPoints = monoMock(GofpRelayPointRepository.class);
        var gofpClients = new GofpClientRepositoryAdapter(repositories.getRepository(GofpClientR2dbcRepository.class));
        var evaluations = new EvaluationApplicationService(mock(SentimentAnalysisUseCase.class), ratings, actorRating,
                freelancerProfiles, clientProfiles, relayPoints, tenants);
        var clients = new GofpClientService(gofpClients, monoMock(GofpUserRepository.class), loyalty, clientProfiles,
                tenants, mock(ICreateClientProfileUseCase.class));

        // data: one client (actor-core profile + Go client row at the profile id), two couriers
        clientProfile = clientProfiles.save(ClientProfile.create(TENANT, CLIENT_ACTOR)).block();
        courier1Profile = freelancerProfiles.save(FreelancerProfile.create(TENANT, COURIER_1, List.of(), List.of())).block();
        freelancerProfiles.save(FreelancerProfile.create(TENANT, COURIER_2, List.of(), List.of())).block();
        try (Connection conn = jdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO gofp_users (id, core_user_id, first_name, last_name, email) VALUES ('"
                    + CLIENT_ACTOR + "', '" + CLIENT_ACTOR + "', 'Ada', 'Client', 'ada+" + CLIENT_ACTOR + "@example.test')");
        }
        GofpClient goClient = new GofpClient();
        goClient.setId(clientProfile.id());
        goClient.setCoreClientId(clientProfile.id());
        goClient.setCoreUserId(CLIENT_ACTOR);
        goClient.setStatus(com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus.ACTIVE);
        goClient.setCreatedAt(Instant.now());
        goClient.setUpdatedAt(Instant.now());
        gofpClients.save(goClient).block();

        web = WebTestClient.bindToController(new EvaluationController(evaluations), new GofpClientController(clients))
                .httpMessageCodecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(json));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(json));
                })
                .controllerAdvice(new TntRoleExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new HeaderUserResolver()))
                .webFilter((exchange, chain) -> {
                    var auth = new TestingAuthenticationToken("user", "n/a",
                            PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList());
                    auth.setAuthenticated(true);
                    return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                })
                .build();
    }

    @Test
    @DisplayName("evaluations -> core ratings and profile averages; completed deliveries -> one credit each -> tier")
    void evaluationAverageAndTier() throws Exception {
        UUID d1 = UUID.randomUUID(), d2 = UUID.randomUUID();

        // two couriers rate the client (profile id, as the app sends it): 4 then 2
        evaluate(COURIER_1, d1, clientProfile.id(), 4, "DP_TO_CLIENT").expectStatus().isOk()
                .expectBody().jsonPath("$.evaluatorId").isEqualTo(COURIER_1.toString())
                .jsonPath("$.evaluatedId").isEqualTo(CLIENT_ACTOR.toString())
                .jsonPath("$.subjectKind").isEqualTo("CLIENT");
        evaluate(COURIER_2, d2, clientProfile.id(), 2, "DP_TO_CLIENT").expectStatus().isOk();
        // the client rates the courier of d1
        evaluate(CLIENT_ACTOR, d1, courier1Profile.id(), 5, "CLIENT_TO_DP").expectStatus().isOk()
                .expectBody().jsonPath("$.subjectKind").isEqualTo("DELIVERY_PERSON");

        // a second rating of the same kind for the same delivery and rater: 409; in someone else's name: 403
        evaluate(COURIER_1, d1, clientProfile.id(), 1, "DP_TO_CLIENT").expectStatus().isEqualTo(409);
        web.post().uri("/api/v1/evaluations").header("X-Test-User", COURIER_2.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"deliveryId\":\"" + UUID.randomUUID() + "\",\"evaluatorId\":\"" + COURIER_1
                        + "\",\"evaluatedId\":\"" + clientProfile.id() + "\",\"rating\":1,\"type\":\"DP_TO_CLIENT\"}")
                .exchange().expectStatus().isForbidden();

        try (Connection conn = jdbc()) {
            assertThat(count(conn, "SELECT count(*) FROM tnt_tp_ratings WHERE tenant_id = '" + TENANT + "'")).isEqualTo(3);
            assertThat(text(conn, "SELECT rating_score || '/' || rating_total FROM tnt_actor.client_profiles WHERE id = '"
                    + clientProfile.id() + "'")).isEqualTo("3/2");
            assertThat(text(conn, "SELECT rating_score || '/' || rating_total FROM tnt_actor.freelancer_profiles WHERE id = '"
                    + courier1Profile.id() + "'")).isEqualTo("5/1");
        }

        // 50 completed deliveries of the client (delivery-core message shape), the first one delivered twice
        for (int i = 1; i <= 50; i++) {
            completed("MISSION-" + i);
        }
        completed("MISSION-1");
        try (Connection conn = jdbc()) {
            // the listener handles each message before returning: nothing left in flight here
            assertThat(count(conn, "SELECT count(*) FROM tnt_loyalty_transactions")).as("one movement per delivery")
                    .isEqualTo(50);
            assertThat(text(conn, "SELECT lifetime_points || '/' || current_tier FROM tnt_loyalty_accounts "
                    + "WHERE tenant_id = '" + TENANT + "' AND third_party_id = '" + CLIENT_ACTOR + "'"))
                    .isEqualTo("500/SILVER");
        }

        // the Go client shows the core average, tier and order count
        web.get().uri("/api/v1/gofp/clients/" + clientProfile.id()).header("X-Test-User", CLIENT_ACTOR.toString())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rating").isEqualTo(3.0)
                .jsonPath("$.loyaltyStatus").isEqualTo("SILVER")
                .jsonPath("$.totalOrders").isEqualTo(50);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static WebTestClient.ResponseSpec evaluate(UUID caller, UUID delivery, UUID evaluated, int rating, String type) {
        return web.post().uri("/api/v1/evaluations").header("X-Test-User", caller.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"deliveryId\":\"" + delivery + "\",\"evaluatedId\":\"" + evaluated + "\",\"rating\":"
                        + rating + ",\"type\":\"" + type + "\"}")
                .exchange();
    }

    private static void completed(String deliveryId) {
        String body = "{\"eventType\":\"DeliveryDelivered\",\"schemaVersion\":1,\"aggregateType\":\"DELIVERY\","
                + "\"aggregateId\":\"" + deliveryId + "\",\"tenantId\":\"" + TENANT + "\","
                + "\"previousState\":{\"status\":\"OUT_FOR_DELIVERY\"},"
                + "\"payload\":{\"deliveryId\":\"" + deliveryId + "\",\"status\":\"DELIVERED\",\"senderId\":\""
                + CLIENT_ACTOR + "\"}}";
        try {
            missionCompleted.onMissionCompleted(new ConsumerRecord<>(
                    com.yowyob.tiibntick.common.kafka.TntTopics.DELIVERY_EVENTS, 0, 0L, deliveryId, body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T withPermissions(T target) {
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(
                mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        return (T) factory.getProxy();
    }

    /** A mock whose reactive methods complete empty instead of returning null. */
    private static <T> T monoMock(Class<T> type) {
        return mock(type, inv -> {
            Class<?> r = inv.getMethod().getReturnType();
            if (Mono.class.equals(r)) return Mono.empty();
            if (Flux.class.equals(r)) return Flux.empty();
            return null;
        });
    }

    private static Connection jdbc() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String text(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Builds the {@code @CurrentUser} context from the {@code X-Test-User} header. */
    static class HeaderUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter p) {
            return p.hasParameterAnnotation(CurrentUser.class)
                    && TntSecurityContext.class.isAssignableFrom(p.getParameterType());
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter p, BindingContext ctx, ServerWebExchange exchange) {
            String user = exchange.getRequest().getHeaders().getFirst("X-Test-User");
            return Mono.justOrEmpty(user == null ? null : TntSecurityContext.builder()
                    .userId(UUID.fromString(user)).tenantId(TENANT).authenticated(true).build());
        }
    }
}
