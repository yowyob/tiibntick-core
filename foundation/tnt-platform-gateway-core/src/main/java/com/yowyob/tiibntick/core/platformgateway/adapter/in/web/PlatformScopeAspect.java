package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.yowyob.tiibntick.common.security.PermissionMatcher;
import com.yowyob.tiibntick.core.platformgateway.domain.exception.TntPlatformGatewayException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * Reactive AOP aspect that enforces {@link RequirePlatformScope} on methods returning
 * {@link Mono} or {@link Flux} — mirrors {@code tnt-roles-core}'s
 * {@code TntPermissionAspect} exactly, but reads the current
 * {@link PlatformClientAuthenticationToken}'s scopes from the reactive
 * {@code SecurityContext} instead of a JWT's permissions, and matches via the shared
 * {@code PermissionMatcher} (see
 * {@code docs/auth/platform-client-management-design.md} §2.4).
 *
 * @author MANFOUO Braun
 */
@Aspect
public class PlatformScopeAspect {

    private static final Logger log = LoggerFactory.getLogger(PlatformScopeAspect.class);

    @Around("@annotation(com.yowyob.tiibntick.core.platformgateway.adapter.in.web.RequirePlatformScope)")
    public Object enforceMethodScope(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePlatformScope annotation = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(RequirePlatformScope.class);
        if (annotation == null) return joinPoint.proceed();
        return enforce(joinPoint, annotation);
    }

    @Around("@within(com.yowyob.tiibntick.core.platformgateway.adapter.in.web.RequirePlatformScope)"
            + " && !@annotation(com.yowyob.tiibntick.core.platformgateway.adapter.in.web.RequirePlatformScope)")
    public Object enforceClassScope(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePlatformScope annotation = joinPoint.getTarget().getClass().getAnnotation(RequirePlatformScope.class);
        if (annotation == null) return joinPoint.proceed();
        return enforce(joinPoint, annotation);
    }

    private Object enforce(ProceedingJoinPoint joinPoint, RequirePlatformScope annotation) throws Throwable {
        String resource = annotation.resource();
        String action = annotation.action();

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();

        Mono<Void> scopeCheck = currentScopes()
                .flatMap(scopes -> PermissionMatcher.matchesAny(scopes, resource, action)
                        ? Mono.<Void>empty()
                        : Mono.error(TntPlatformGatewayException.scopeForbidden(resource, action)))
                .switchIfEmpty(Mono.error(TntPlatformGatewayException.scopeForbidden(resource, action)));

        if (Mono.class.isAssignableFrom(returnType)) {
            return scopeCheck.then(Mono.defer(() -> {
                try {
                    return (Mono<?>) joinPoint.proceed();
                } catch (Throwable t) {
                    return Mono.error(t);
                }
            })).doOnSubscribe(s -> log.debug("Platform scope check: resource={} action={}", resource, action));
        }

        if (Flux.class.isAssignableFrom(returnType)) {
            return scopeCheck.thenMany(Flux.defer(() -> {
                try {
                    return (Flux<?>) joinPoint.proceed();
                } catch (Throwable t) {
                    return Flux.error(t);
                }
            })).doOnSubscribe(s -> log.debug("Platform scope check (Flux): resource={} action={}", resource, action));
        }

        throw new UnsupportedOperationException(
                "@RequirePlatformScope is only supported on methods returning Mono<?> or Flux<?>. "
                        + "Method: " + joinPoint.getSignature().toShortString());
    }

    private Mono<java.util.Set<String>> currentScopes() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .filter(PlatformClientAuthenticationToken.class::isInstance)
                .map(auth -> ((PlatformClientAuthenticationToken) auth).getScopes());
    }
}
