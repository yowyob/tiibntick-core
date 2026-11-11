package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Drives the filter through a real WebFlux dispatcher (routing, argument binding, response writing)
 * with a controller shaped like the tenant-scoped delivery controllers.
 */
class TenantPathGuardWebFilterTest {

    private static final UUID MY_TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    @RestController
    static class TenantScopedController {
        @GetMapping("/api/v1/tenants/{tenantId}/deliveries")
        Mono<String> list(@PathVariable UUID tenantId) {
            return Mono.just("deliveries-of-" + tenantId);
        }

        @GetMapping("/api/v1/other")
        Mono<String> other() {
            return Mono.just("other");
        }
    }

    private WebTestClient clientFor(TntSecurityContext ctx) {
        ResolveCurrentUserUseCase resolver = new ResolveCurrentUserUseCase() {
            @Override public Mono<TntSecurityContext> resolveCurrentContext() { return Mono.just(ctx); }
            @Override public Mono<TntUserIdentity> resolveCurrentIdentity() { return Mono.just(TntUserIdentity.from(ctx)); }
            @Override public Mono<TntSecurityContext> resolveCurrentContextOrAnonymous() { return Mono.just(ctx); }
        };
        return WebTestClient.bindToController(new TenantScopedController())
                .webFilter(new TenantPathGuardWebFilter(resolver))
                .build();
    }

    private static TntSecurityContext user(UUID tenant, Set<String> permissions) {
        return TntSecurityContext.builder()
                .userId(UUID.randomUUID()).tenantId(tenant).authenticated(true)
                .permissions(permissions).build();
    }

    @Test
    @DisplayName("a user may read the tenant of their own token")
    void ownTenantIsAllowed() {
        clientFor(user(MY_TENANT, Set.of()))
                .get().uri("/api/v1/tenants/{t}/deliveries", MY_TENANT)
                .exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("deliveries-of-" + MY_TENANT);
    }

    @Test
    @DisplayName("editing the tenant id in the URL is refused with 403 and the controller is never reached")
    void otherTenantIsForbidden() {
        clientFor(user(MY_TENANT, Set.of("delivery:read")))
                .get().uri("/api/v1/tenants/{t}/deliveries", OTHER_TENANT)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody().jsonPath("$.status").isEqualTo(403);
    }

    @Test
    @DisplayName("a platform administrator (permission *) may cross tenants")
    void administratorMayCrossTenants() {
        clientFor(user(MY_TENANT, Set.of("*")))
                .get().uri("/api/v1/tenants/{t}/deliveries", OTHER_TENANT)
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("an unauthenticated request is left to the security chain, not decided here")
    void anonymousIsNotDecidedByTheGuard() {
        clientFor(TntSecurityContext.anonymous())
                .get().uri("/api/v1/tenants/{t}/deliveries", OTHER_TENANT)
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("a malformed tenant id is passed through so the controller answers 400")
    void malformedTenantIdPassesThrough() {
        clientFor(user(MY_TENANT, Set.of()))
                .get().uri("/api/v1/tenants/not-a-uuid/deliveries")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("paths outside /api/v1/tenants/{id} are never touched")
    void otherPathsAreUntouched() {
        clientFor(user(MY_TENANT, Set.of()))
                .get().uri("/api/v1/other")
                .exchange().expectStatus().isOk();
    }
}
