package com.yowyob.tiibntick.core.geo.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubApprovalListener;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository;
import com.yowyob.tiibntick.core.geo.application.service.RelayHubApprovalService;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubApprovalChange;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * D-141 over HTTP: the core approval routes of a relay hub are reserved to the TiiBnTick administrator
 * ({@code relay:approve}, granted to no role) and follow the approval machine (D-138, D-139). Real permission aspect,
 * real role registry, real {@link RelayHubApprovalService} on an in-memory repository.
 */
class RelayHubApprovalRoutesHttpTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String BASE = "/api/v1/geo/tenants/{tenantId}/hubs/{hubId}/";

    private final Map<UUID, RelayHub> store = new HashMap<>();
    private final IRelayHubRepository repository = mock(IRelayHubRepository.class);
    private final IRevokeRelayHubUseCase revoke = mock(IRevokeRelayHubUseCase.class);
    private final List<RelayHubApprovalChange> told = new ArrayList<>();
    private final List<String> transitions = new ArrayList<>();

    @BeforeEach
    void inMemoryRepository() {
        when(repository.findById(any(), any())).thenAnswer(i -> Mono.justOrEmpty(store.get(i.<UUID>getArgument(0))));
        when(repository.save(any())).thenAnswer(i -> {
            RelayHub hub = i.getArgument(0);
            store.put(hub.id(), hub);
            return Mono.just(hub);
        });
    }

    private UUID hub(HubApprovalStatus approval) {
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        RelayHub hub = RelayHub.rehydrate(UUID.randomUUID(), TENANT, null, RoadNodeId.generate(), 4, 0, "op",
                HubStatus.ACTIVE, approval, t, t, null, null, null);
        store.put(hub.id(), hub);
        return hub.id();
    }

    private WebTestClient as(String role) {
        StaticListableBeanFactory listeners = new StaticListableBeanFactory();
        listeners.addBean("recorder", (IRelayHubApprovalListener) c -> Mono.fromRunnable(() -> {
            told.add(c);
            transitions.add(c.previous() + ">" + c.hub().approvalStatus()); // the hub instance changes afterwards
        }));
        RelayHubAdminController controller = new RelayHubAdminController(revoke,
                new RelayHubApprovalService(repository, listeners.getBeanProvider(IRelayHubApprovalListener.class)));
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(new TntPermissionEvaluator(
                mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry())));
        var auth = new UsernamePasswordAuthenticationToken(ADMIN.toString(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        TntSecurityContext ctx = TntSecurityContext.builder().userId(ADMIN).tenantId(TENANT).authenticated(true).build();
        return WebTestClient.bindToController((RelayHubAdminController) factory.getProxy())
                .controllerAdvice(new TntRoleExceptionHandler(), new GeoExceptionHandler())
                .argumentResolvers(r -> r.addCustomResolver(new FixedContext(ctx)))
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    private WebTestClient.ResponseSpec post(WebTestClient client, String route, UUID hubId, String body) {
        var spec = client.post().uri(BASE + route, TENANT, hubId);
        return body == null ? spec.exchange()
                : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    @ParameterizedTest(name = "{0}: 403 on every approval route and on the revocation")
    @EnumSource(value = TntRole.class, names = "TNT_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("only the TiiBnTick administrator holds relay:approve and relay:revoke")
    void everyOtherRoleIsForbidden(TntRole role) {
        UUID hubId = hub(HubApprovalStatus.PENDING);
        WebTestClient client = as(role.code());
        post(client, "approve", hubId, null).expectStatus().isForbidden();
        post(client, "reject", hubId, "{\"reason\":\"x\"}").expectStatus().isForbidden();
        post(client, "suspend", hubId, null).expectStatus().isForbidden();
        post(client, "reactivate", hubId, null).expectStatus().isForbidden();
        post(client, "revoke", hubId, "{\"reason\":\"x\"}").expectStatus().isForbidden();
        assertThat(store.get(hubId).approvalStatus()).isEqualTo(HubApprovalStatus.PENDING);
        verifyNoInteractions(revoke);
        assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("the administrator walks the machine: approve, suspend, reactivate; refused transitions are 409")
    void administratorTransitions() {
        WebTestClient admin = as("TNT_ADMIN");
        UUID hubId = hub(HubApprovalStatus.PENDING);

        post(admin, "reactivate", hubId, null).expectStatus().isEqualTo(409);
        post(admin, "suspend", hubId, null).expectStatus().isEqualTo(409);
        post(admin, "approve", hubId, null).expectStatus().isOk()
                .expectBody().jsonPath("$.approvalStatus").isEqualTo("APPROVED")
                .jsonPath("$.operatingStatus").isEqualTo("ACTIVE");
        post(admin, "approve", hubId, null).expectStatus().isOk()
                .expectBody().jsonPath("$.approvalStatus").isEqualTo("APPROVED");
        post(admin, "reject", hubId, "{\"reason\":\"tard\"}").expectStatus().isEqualTo(409);
        post(admin, "suspend", hubId, "{\"reason\":\"contrôle\"}").expectStatus().isOk()
                .expectBody().jsonPath("$.approvalStatus").isEqualTo("SUSPENDED");
        post(admin, "approve", hubId, null).expectStatus().isEqualTo(409);
        post(admin, "reactivate", hubId, null).expectStatus().isOk()
                .expectBody().jsonPath("$.approvalStatus").isEqualTo("APPROVED");

        assertThat(transitions).as("listeners told of each real change only").containsExactly(
                        "PENDING>APPROVED", "APPROVED>SUSPENDED", "SUSPENDED>APPROVED");
        assertThat(told.get(1).reason()).isEqualTo("contrôle");
        assertThat(told).allMatch(c -> ADMIN.equals(c.actorId()));
    }

    @Test
    @DisplayName("rejection: reason mandatory (400), PENDING -> REJECTED, then final (409)")
    void rejection() {
        WebTestClient admin = as("TNT_ADMIN");
        UUID hubId = hub(HubApprovalStatus.PENDING);
        post(admin, "reject", hubId, "{\"reason\":\" \"}").expectStatus().isBadRequest();
        post(admin, "reject", hubId, null).expectStatus().isBadRequest();
        post(admin, "reject", hubId, "{\"reason\":\"Pièces illisibles\"}").expectStatus().isOk()
                .expectBody().jsonPath("$.approvalStatus").isEqualTo("REJECTED");
        post(admin, "approve", hubId, null).expectStatus().isEqualTo(409);
        assertThat(store.get(hubId).approvalStatus()).isEqualTo(HubApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("revoked hub: every approval route answers 409; unknown hub: 404")
    void revokedAndUnknown() {
        WebTestClient admin = as("TNT_ADMIN");
        UUID revoked = hub(HubApprovalStatus.REVOKED);
        for (String route : List.of("approve", "suspend", "reactivate")) {
            post(admin, route, revoked, null).expectStatus().isEqualTo(409);
        }
        post(admin, "reject", revoked, "{\"reason\":\"x\"}").expectStatus().isEqualTo(409);
        post(admin, "approve", UUID.randomUUID(), null).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("the revocation route stays on relay:revoke and is reachable by the administrator")
    void revocationStillWorks() {
        UUID hubId = hub(HubApprovalStatus.APPROVED);
        RelayHub hub = store.get(hubId);
        hub.revoke("Fraude", ADMIN);
        when(revoke.revoke(TENANT, hubId, "Fraude", ADMIN)).thenReturn(Mono.just(new RelayHubRevocation(hub, List.of(), true)));
        post(as("TNT_ADMIN"), "revoke", hubId, "{\"reason\":\"Fraude\"}").expectStatus().isOk()
                .expectBody().jsonPath("$.approvalStatus").isEqualTo("REVOKED");
    }

    static class FixedContext implements HandlerMethodArgumentResolver {
        private final TntSecurityContext ctx;

        FixedContext(TntSecurityContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter p, BindingContext c, ServerWebExchange e) {
            return Mono.just(ctx);
        }
    }
}
