package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code relay:revoke} (decision D-129: revoking a relay point is definitive) is an explicit permission held by the
 * platform administrator only, through the REAL role registry: no other canonical role grants it, not even the
 * relay operator or the agency manager.
 */
class TntRelayRevokePermissionTest {

    private final TntPermissionEvaluator evaluator =
            new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());

    private Mono<Boolean> check(TntRole role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.code())));
        return evaluator.canFromCurrentContext("relay", "revoke")
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    @Test
    void theConstantIsRelayRevoke() {
        assertThat(TntPermission.RELAY_REVOKE).isEqualTo("relay:revoke");
    }

    @Test
    @DisplayName("TNT_ADMIN may revoke a relay")
    void adminMayRevoke() {
        StepVerifier.create(check(TntRole.TNT_ADMIN)).expectNext(true).verifyComplete();
    }

    @ParameterizedTest(name = "{0} may NOT revoke a relay")
    @EnumSource(value = TntRole.class, names = "TNT_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void noOtherRoleMayRevoke(TntRole role) {
        assertThat(role.defaultPermissions()).doesNotContain(TntPermission.RELAY_REVOKE);
        StepVerifier.create(check(role)).expectNext(false).verifyComplete();
    }
}
