package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("A notification stream belongs to its freelancer")
class NotificationStreamAccessGuardTest {

    private final UUID user = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final GofpFreelancerRepository repo = mock(GofpFreelancerRepository.class);

    private NotificationStreamAccessGuard guardFor(TntSecurityContext ctx) {
        ResolveCurrentUserUseCase resolver = new ResolveCurrentUserUseCase() {
            @Override public Mono<TntSecurityContext> resolveCurrentContext() { return Mono.just(ctx); }
            @Override public Mono<TntUserIdentity> resolveCurrentIdentity() { return Mono.just(TntUserIdentity.from(ctx)); }
            @Override public Mono<TntSecurityContext> resolveCurrentContextOrAnonymous() { return Mono.just(ctx); }
        };
        return new NotificationStreamAccessGuard(resolver, repo);
    }

    private TntSecurityContext ctx(Set<String> permissions) {
        return TntSecurityContext.builder().userId(user).tenantId(UUID.randomUUID())
                .authenticated(true).permissions(permissions).build();
    }

    @Test
    @DisplayName("the freelancer's own profile id is allowed")
    void ownProfile() {
        GofpFreelancer f = new GofpFreelancer();
        f.setId(profile);
        when(repo.findByCoreUserId(user)).thenReturn(Mono.just(f));
        StepVerifier.create(guardFor(ctx(Set.of())).assertOwner(profile)).verifyComplete();
    }

    @Test
    @DisplayName("somebody else's profile id is refused")
    void otherProfile() {
        GofpFreelancer f = new GofpFreelancer();
        f.setId(profile);
        when(repo.findByCoreUserId(user)).thenReturn(Mono.just(f));
        StepVerifier.create(guardFor(ctx(Set.of())).assertOwner(UUID.randomUUID()))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("a caller without a freelancer profile is refused")
    void noProfile() {
        when(repo.findByCoreUserId(user)).thenReturn(Mono.empty());
        StepVerifier.create(guardFor(ctx(Set.of())).assertOwner(profile))
                .expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the caller's own user id and a platform administrator are allowed")
    void userIdAndAdmin() {
        StepVerifier.create(guardFor(ctx(Set.of())).assertOwner(user)).verifyComplete();
        StepVerifier.create(guardFor(ctx(Set.of("*"))).assertOwner(UUID.randomUUID())).verifyComplete();
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymous() {
        StepVerifier.create(guardFor(TntSecurityContext.anonymous()).assertOwner(profile))
                .expectError(AccessDeniedException.class).verify();
    }
}
