package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.AssignMediatorCommand;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Audit n°7 · #6 — {@code tnt-dispute-core}'s controllers previously
 * carried only {@code @PreAuthorize("isAuthenticated()")}, which rejected anonymous callers
 * but let any authenticated role reach mediator-tier actions on any tenant's dispute.
 *
 * <p>Same pattern as {@code WalletControllerPermissionEnforcementTest} (tnt-billing-wallet):
 * proves {@code @RequirePermission} on {@link DisputeCommandService} is enforced end-to-end
 * via a real {@link TntPermissionAspect} AOP proxy — not just present in source.
 *
 * @author MANFOUO Braun
 */
class DisputePermissionEnforcementTest {

    @Test
    @DisplayName("openDispute() - rejected for a caller lacking dispute:create")
    void openDispute_withoutPermission_isRejected() {
        DisputeCommandService proxied = buildProxiedService();

        var noPermissionAuth = new TestingAuthenticationToken("user", "n/a", List.of());
        noPermissionAuth.setAuthenticated(true);

        StepVerifier.create(proxied.openDispute(buildOpenCmd())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(noPermissionAuth)))
                .expectError(TntRoleException.class)
                .verify();
    }

    @Test
    @DisplayName("openDispute() - allowed through the AOP guard once dispute:create is granted")
    void openDispute_withPermission_isNotBlockedByAop() {
        IDisputeRepository repository = mock(IDisputeRepository.class);
        IDisputeReferenceGenerator referenceGenerator = mock(IDisputeReferenceGenerator.class);
        when(referenceGenerator.nextReference()).thenReturn(Mono.just(DisputeReference.forSequence(1)));
        when(repository.existsActiveDisputeForPackage(anyString(), anyString())).thenReturn(Mono.just(false));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        DisputeCommandService proxied = buildProxiedService(repository, referenceGenerator);

        var withPermissionAuth = new TestingAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("dispute:create")));
        withPermissionAuth.setAuthenticated(true);

        StepVerifier.create(proxied.openDispute(buildOpenCmd())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(withPermissionAuth)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("assignMediator() - rejected for a caller holding only dispute:create (no dispute:resolve)")
    void assignMediator_withoutResolvePermission_isRejected() {
        DisputeCommandService proxied = buildProxiedService();

        var claimantOnlyAuth = new TestingAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("dispute:create")));
        claimantOnlyAuth.setAuthenticated(true);

        AssignMediatorCommand cmd = new AssignMediatorCommand(
                DisputeId.of(java.util.UUID.randomUUID().toString()), "tenant-01", "mediator-1", "requester-1");

        StepVerifier.create(proxied.assignMediator(cmd)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(claimantOnlyAuth)))
                .expectError(TntRoleException.class)
                .verify();
    }

    private DisputeCommandService buildProxiedService() {
        return buildProxiedService(mock(IDisputeRepository.class), mock(IDisputeReferenceGenerator.class));
    }

    private DisputeCommandService buildProxiedService(IDisputeRepository repository, IDisputeReferenceGenerator referenceGenerator) {
        DisputeCommandService realService = new DisputeCommandService(
                repository,
                mock(IDisputeEventPublisher.class, invocation -> Mono.empty()),
                mock(IDisputeNotificationPort.class, invocation -> Mono.empty()),
                mock(IDeliveryStatusPort.class, invocation -> Mono.empty()),
                mock(IBillingCompensationPort.class),
                mock(IBlockchainProofPort.class),
                referenceGenerator);

        TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), registry);
        TntPermissionAspect aspect = new TntPermissionAspect(evaluator);

        AspectJProxyFactory factory = new AspectJProxyFactory(realService);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private OpenDisputeCommand buildOpenCmd() {
        return new OpenDisputeCommand(
                "tenant-01",
                "client-abc",
                ClaimantType.CLIENT,
                "freelancer-xyz",
                RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED,
                DisputeCategory.MISSION_GO,
                DisputePriority.HIGH,
                "mission-001",
                "pkg-001",
                "TKG-001",
                "Package was damaged on delivery",
                null,
                null,
                false);
    }
}
