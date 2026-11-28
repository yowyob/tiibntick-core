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
 * {@code relay:approve} (decision D-141: approving, rejecting, suspending or reactivating a relay hub through the core
 * routes) is an explicit permission held by the platform administrator only, through the REAL role registry: no other canonical role grants it, not even the
 * relay operator or the agency manager.
 */
class TntRelayApprovePermissionTest {

    private final TntPermissionEvaluator evaluator =
            new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());

    private Mono<Boolean> check(TntRole role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.code())));
        return evaluator.canFromCurrentContext("relay", "approve")
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    @Test
    void theConstantIsRelayApprove() {
        assertThat(TntPermission.RELAY_APPROVE).isEqualTo("relay:approve");
    }

    @Test
    @DisplayName("TNT_ADMIN may approve, reject, suspend or reactivate a relay hub")
    void adminMayApprove() {
        StepVerifier.create(check(TntRole.TNT_ADMIN)).expectNext(true).verifyComplete();
    }

    @ParameterizedTest(name = "{0} may NOT change a relay hub approval")
    @EnumSource(value = TntRole.class, names = "TNT_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void noOtherRoleMayApprove(TntRole role) {
        assertThat(role.defaultPermissions()).doesNotContain(TntPermission.RELAY_APPROVE);
        StepVerifier.create(check(role)).expectNext(false).verifyComplete();
    }
}
