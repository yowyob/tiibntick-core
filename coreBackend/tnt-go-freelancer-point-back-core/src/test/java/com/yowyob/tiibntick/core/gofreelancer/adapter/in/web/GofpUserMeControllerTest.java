package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpUserProvisioningService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lot C-56 — the caller's own name, kept TiiBnTick-side (gofp_users), resolved from the token. */
@DisplayName("C-56 — GET/PUT /api/v1/gofp/users/me")
class GofpUserMeControllerTest {

    static final UUID ME = UUID.fromString("11111111-0000-0000-0000-0000000005c6");
    static final UUID OTHER = UUID.fromString("22222222-0000-0000-0000-0000000005c6");

    private final Map<UUID, GofpUser> store = new HashMap<>();
    private final GofpUserRepository users = mock(GofpUserRepository.class);

    @BeforeEach
    void setUp() {
        when(users.findByCoreUserId(any())).thenAnswer(inv -> Mono.justOrEmpty(store.get(inv.<UUID>getArgument(0))));
        when(users.save(any())).thenAnswer(inv -> {
            GofpUser u = inv.getArgument(0);
            store.put(u.getCoreUserId(), u);
            return Mono.just(u);
        });
    }

    private WebTestClient as(UUID userId) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).authenticated(userId != null)
                .roles(Set.of("ROLE_CLIENT")).build();
        return WebTestClient.bindToController(new GofpUserMeController(users, new GofpUserProvisioningService(users)))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new RelayPointAccessControllerTest.FixedContextResolver(ctx)))
                .build();
    }

    @Test
    @DisplayName("PUT records the caller's name, GET reads it back; another user keeps no name")
    void putThenGet() {
        as(ME).put().uri("/api/v1/gofp/users/me").bodyValue(Map.of("firstName", "  Awa ", "lastName", "Ngo  Bell"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.firstName").isEqualTo("Awa").jsonPath("$.lastName").isEqualTo("Ngo Bell");
        assertThat(store.get(ME).getFirstName()).isEqualTo("Awa");

        as(ME).get().uri("/api/v1/gofp/users/me").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.firstName").isEqualTo("Awa");
        as(OTHER).get().uri("/api/v1/gofp/users/me").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.firstName").isEmpty();
    }

    @Test
    @DisplayName("an empty first name is refused (400), nothing written")
    void emptyNameRefused() {
        as(ME).put().uri("/api/v1/gofp/users/me").bodyValue(Map.of("firstName", "   "))
                .exchange().expectStatus().isBadRequest();
        assertThat(store).isEmpty();
    }
}
