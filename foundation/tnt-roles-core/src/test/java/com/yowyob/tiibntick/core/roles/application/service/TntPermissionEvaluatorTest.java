package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TntPermissionEvaluatorTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();

    @Mock
    private ReactivePermissionResolver permissionResolver;

    private TntPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new TntPermissionEvaluator(permissionResolver, new TntRoleDefinitionRegistry());
    }

    // ─── can() / cannot() ────────────────────────────────────────

    @Test
    void can_withExactPermission_shouldReturnTrue() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(TntPermission.MISSION_CREATE)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.can(ctx, "mission", "create"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void can_withWildcardActionPermission_shouldReturnTrue() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of("mission:*")));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.can(ctx, "mission", "assign"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void can_withGlobalWildcard_shouldGrantEverything() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(TntPermission.ALL)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.can(ctx, "billing", "write"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void can_withScopedPermissionAndMatchingAgency_shouldReturnTrue() {
        String scopedPerm = "mission:create#AGENCY:" + AGENCY_ID;
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(scopedPerm)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID, AGENCY_ID);

        StepVerifier.create(evaluator.can(ctx, "mission", "create"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void can_withScopedPermissionAndDifferentAgency_shouldReturnFalse() {
        UUID differentAgency = UUID.randomUUID();
        String scopedPerm = "mission:create#AGENCY:" + differentAgency;
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(scopedPerm)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID, AGENCY_ID);

        StepVerifier.create(evaluator.can(ctx, "mission", "create"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void can_withTenantScopedPermission_shouldReturnTrue() {
        String tenantPerm = "mission:create#TENANT";
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(tenantPerm)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.can(ctx, "mission", "create"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void can_withNoMatchingPermission_shouldReturnFalse() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(TntPermission.DELIVERY_READ)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.can(ctx, "mission", "create"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void cannot_invertsCanResult() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(TntPermission.DELIVERY_READ)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.cannot(ctx, "mission", "create"))
                .expectNext(true)
                .verifyComplete();
    }

    // ─── assertCan() ─────────────────────────────────────────────

    @Test
    void assertCan_withPermission_shouldCompleteEmpty() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(TntPermission.BILLING_WRITE)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.assertCan(ctx, "billing", "write"))
                .verifyComplete();
    }

    @Test
    void assertCan_withoutPermission_shouldEmitForbiddenError() {
        when(permissionResolver.resolvePermissions(any(), any()))
                .thenReturn(Mono.just(Set.of(TntPermission.BILLING_READ)));

        TntPermissionContext ctx = TntPermissionContext.of(USER_ID, TENANT_ID);

        StepVerifier.create(evaluator.assertCan(ctx, "billing", "write"))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_FORBIDDEN");
                })
                .verify();
    }

    // ─── canFromCurrentContext() ──────────────────────────────────

    @Test
    void canFromCurrentContext_withPermissionInSecurityContext_shouldReturnTrue() {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                List.of(new SimpleGrantedAuthority(TntPermission.REPORT_EXPORT))
        );
        var secCtx = Mono.just(new SecurityContextImpl(auth));

        StepVerifier.create(
                evaluator.canFromCurrentContext("report", "export")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        .expectNext(true)
        .verifyComplete();
    }

    @Test
    void canFromCurrentContext_withoutPermission_shouldReturnFalse() {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                List.of(new SimpleGrantedAuthority(TntPermission.BILLING_READ))
        );
        var secCtx = Mono.just(new SecurityContextImpl(auth));

        StepVerifier.create(
                evaluator.canFromCurrentContext("billing", "write")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        .expectNext(false)
        .verifyComplete();
    }

    @Test
    void canFromCurrentContext_withNoSecurityContext_shouldReturnFalse() {
        StepVerifier.create(evaluator.canFromCurrentContext("mission", "create"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void assertCanFromCurrentContext_withPermission_shouldCompleteEmpty() {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                List.of(new SimpleGrantedAuthority(TntPermission.MISSION_CREATE))
        );
        var secCtx = Mono.just(new SecurityContextImpl(auth));

        StepVerifier.create(
                evaluator.assertCanFromCurrentContext("mission", "create")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        .verifyComplete();
    }

    @Test
    void assertCanFromCurrentContext_withoutPermission_shouldEmitForbidden() {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of()
        );
        var secCtx = Mono.just(new SecurityContextImpl(auth));

        StepVerifier.create(
                evaluator.assertCanFromCurrentContext("admin", "roles")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        .expectError(TntRoleException.class)
        .verify();
    }
}
