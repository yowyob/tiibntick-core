package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Decision D-162 — the probe of 2026-11-27, inverted: a scoped {@code ROLE_TNT_ADMIN#TENANT:<any tenant>} (only a
 * Kernel claim can produce it, and a tenant owner can mint that code in their own tenant) no longer expands to
 * {@code *}; the exact authority the Core's converter adds still does.
 */
class ForgedTntAdminAuthorityTest {

    private final TntPermissionEvaluator evaluator = evaluator();

    private static TntPermissionEvaluator evaluator() {
        ReactivePermissionResolver resolver = mock(ReactivePermissionResolver.class);
        when(resolver.resolvePermissions(any(), any())).thenReturn(Mono.just(Set.of()));
        return new TntPermissionEvaluator(resolver, new TntRoleDefinitionRegistry());
    }

    private Mono<Boolean> systemAdminWith(String... authorities) {
        UUID tenant = UUID.randomUUID();
        List<SimpleGrantedAuthority> list = new java.util.ArrayList<>();
        for (String a : authorities) list.add(new SimpleGrantedAuthority(a));
        list.add(new SimpleGrantedAuthority("TENANT_" + tenant));
        var auth = new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), "n/a", list);
        return evaluator.canFromCurrentContext("system", "admin")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    @Test
    void aScopedTntAdminFromAClaimGrantsNothing() {
        StepVerifier.create(systemAdminWith("ROLE_TNT_ADMIN#TENANT:" + UUID.randomUUID()))
                .expectNext(false).verifyComplete();
    }

    @Test
    void theExactAuthorityOfTheConverterStillGrantsEverything() {
        StepVerifier.create(systemAdminWith("ROLE_TNT_ADMIN")).expectNext(true).verifyComplete();
    }
}
