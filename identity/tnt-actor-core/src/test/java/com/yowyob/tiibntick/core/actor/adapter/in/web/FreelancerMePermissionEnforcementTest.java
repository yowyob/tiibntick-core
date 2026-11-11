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
 * {@code GET /freelancers/me} must not depend on TiiBnTick roles in the token: the token does not carry them, so
 * requiring {@code freelancer:read} there made an existing profile look absent and invited the person to create it
 * again. The endpoint only authenticates, then looks the caller's own profile up across spaces.
 */
class FreelancerMePermissionEnforcementTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("/me answers a caller who holds no freelancer:read authority, through findMine")
    void getMyProfile_withoutTiiBnTickRole_isNotBlocked() {
        IFindFreelancerUseCase findUseCase = mock(IFindFreelancerUseCase.class);
        when(findUseCase.findMine(any(), any()))
                .thenReturn(Mono.error(new FreelancerNotFoundException(TENANT_ID, UUID.randomUUID())));

        var noRole = new TestingAuthenticationToken("user", "n/a", List.of());
        noRole.setAuthenticated(true);

        buildClient(findUseCase, noRole)
                .get().uri("/api/v1/freelancers/me")
                .exchange()
                .expectStatus().isNotFound();

        verify(findUseCase).findMine(any(), any());
        verify(findUseCase, never()).findByActorId(any(), any());
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
