package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@DisplayName("A notification feed belongs to its recipient")
class RecipientAccessGuardTest {

    private final UUID user = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final TntUserIdentity caller =
            new TntUserIdentity(user, UUID.randomUUID(), actor, null, null, Set.of(), false);

    private Mono<Void> check(String recipient, String... roles) {
        var auth = new UsernamePasswordAuthenticationToken("u", "n/a",
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
        return RecipientAccessGuard.check(caller, recipient)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    @Test
    @DisplayName("own user id and own actor id are allowed")
    void ownIds() {
        StepVerifier.create(check(user.toString(), "ROLE_CLIENT")).verifyComplete();
        StepVerifier.create(check(actor.toString().toUpperCase(), "ROLE_FREELANCER")).verifyComplete();
    }

    @Test
    @DisplayName("somebody else's id is refused for a client or a freelancer")
    void otherIdRefused() {
        StepVerifier.create(check(UUID.randomUUID().toString(), "ROLE_CLIENT"))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(check(UUID.randomUUID().toString(), "ROLE_FREELANCER"))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("support agents and administrators may read any feed")
    void staffAllowed() {
        StepVerifier.create(check(UUID.randomUUID().toString(), "ROLE_SUPPORT_AGENT")).verifyComplete();
        StepVerifier.create(check(UUID.randomUUID().toString(), "ROLE_TNT_ADMIN")).verifyComplete();
    }

    @Test
    @DisplayName("without any authentication context nothing is allowed")
    void noContext() {
        StepVerifier.create(RecipientAccessGuard.check(caller, UUID.randomUUID().toString()))
                .expectError(AccessDeniedException.class).verify();
    }
}
