package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.application.port.in.IAssociateFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateFreelancerProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IDissociateFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerByOrgUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.ILinkFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IUpdateActorLocationUseCase;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that {@code @RequirePermission(resource = "freelancer", action = "read")} on
 * {@link FreelancerController#getMyProfile} is enforced at runtime and that a caller
 * without the {@code freelancer:read} authority receives HTTP 403, not 500 or 200.
 */
class FreelancerMePermissionEnforcementTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("/me is rejected with 403 for a caller lacking freelancer:read")
    void getMyProfile_withoutPermission_returns403() {
        IFindFreelancerUseCase findUseCase = mock(IFindFreelancerUseCase.class);

        var noPermission = new TestingAuthenticationToken("user", "n/a", List.of());
        noPermission.setAuthenticated(true);

        buildClient(findUseCase, noPermission)
                .get().uri("/api/v1/freelancers/me")
                .exchange()
                .expectStatus().isForbidden();

        verify(findUseCase, never()).findByActorId(any(), any());
    }

    @Test
    @DisplayName("/me is not blocked by AOP guard once freelancer:read is granted")
    void getMyProfile_withPermission_isNotBlockedByAop() {
        IFindFreelancerUseCase findUseCase = mock(IFindFreelancerUseCase.class);
        // Permission passes; domain throws FreelancerNotFoundException (no profile) → 404
        when(findUseCase.findByActorId(any(), any()))
                .thenReturn(Mono.error(new FreelancerNotFoundException(TENANT_ID, UUID.randomUUID())));

        var withPermission = new TestingAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("freelancer:read")));
        withPermission.setAuthenticated(true);

        buildClient(findUseCase, withPermission)
                .get().uri("/api/v1/freelancers/me")
                .exchange()
                .expectStatus().isNotFound();
    }

    private WebTestClient buildClient(IFindFreelancerUseCase findUseCase, Authentication authentication) {
        FreelancerController controller = new FreelancerController(
                mock(ICreateFreelancerProfileUseCase.class),
                findUseCase,
                mock(IAssociateFreelancerUseCase.class),
                mock(IDissociateFreelancerUseCase.class),
                mock(IUpdateActorLocationUseCase.class),
                mock(IRateActorUseCase.class),
                mock(ILinkFreelancerOrgUseCase.class),
                mock(IFindFreelancerByOrgUseCase.class),
                mock(IResolveActorIdentityUseCase.class));

        TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();
        TntPermissionEvaluator evaluator = new TntPermissionEvaluator(
                mock(ReactivePermissionResolver.class), registry);
        TntPermissionAspect aspect = new TntPermissionAspect(evaluator);

        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        FreelancerController proxiedController = factory.getProxy();

        return WebTestClient.bindToController(proxiedController)
                .controllerAdvice(new TntRoleExceptionHandler(), new ActorExceptionHandler())
                .argumentResolvers(r -> r.addCustomResolver(new FixedCurrentUserResolver()))
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .build();
    }

    static class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                             ServerWebExchange exchange) {
            return Mono.just(new TntUserIdentity(
                    UUID.randomUUID(), TENANT_ID, UUID.randomUUID(), null, null, Set.of(), false));
        }
    }
}
