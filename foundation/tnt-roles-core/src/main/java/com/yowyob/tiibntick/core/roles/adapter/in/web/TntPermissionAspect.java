package com.yowyob.tiibntick.core.roles.adapter.in.web;

import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * Reactive AOP aspect that enforces {@link RequirePermission} on methods
 * returning {@link Mono} or {@link Flux}.
 *
 * <p>Intercepts any Spring bean method annotated with {@link RequirePermission}
 * and prepends a permission check using
 * {@link TntPermissionEvaluator#assertCanFromCurrentContext(String, String)}.
 * The check reads the current user's authorities from the Spring Security
 * reactive context — no additional DB roundtrip for hot paths where the JWT
 * already carries the permissions.
 *
 * <p>Non-reactive methods (returning plain Java objects) are NOT supported
 * and will fail fast with an {@link UnsupportedOperationException}.
 *
 * <p>Annotation can be placed on the method or on the class (class-level
 * acts as a default for all methods).
 *
 * @author MANFOUO Braun
 */
@Aspect
public class TntPermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(TntPermissionAspect.class);

    private final TntPermissionEvaluator permissionEvaluator;

    public TntPermissionAspect(TntPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Around("@annotation(com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission)")
    public Object enforceMethodPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission annotation = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(RequirePermission.class);
        if (annotation == null) return joinPoint.proceed();
        return enforce(joinPoint, annotation);
    }

    @Around("@within(com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission)"
            + " && !@annotation(com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission)")
    public Object enforceClassPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission annotation = joinPoint.getTarget().getClass()
                .getAnnotation(RequirePermission.class);
        if (annotation == null) return joinPoint.proceed();
        return enforce(joinPoint, annotation);
    }

    private Object enforce(ProceedingJoinPoint joinPoint, RequirePermission annotation) throws Throwable {
        String resource = annotation.resource();
        String action = annotation.action();
        boolean required = annotation.required();

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return enforceOnMono(joinPoint, resource, action, required);
        }

        if (Flux.class.isAssignableFrom(returnType)) {
            return enforceOnFlux(joinPoint, resource, action, required);
        }

        throw new UnsupportedOperationException(
                "@RequirePermission is only supported on methods returning Mono<?> or Flux<?>. "
                        + "Method: " + joinPoint.getSignature().toShortString()
        );
    }

    private Mono<?> enforceOnMono(
            ProceedingJoinPoint joinPoint,
            String resource,
            String action,
            boolean required) {
        Mono<Void> permissionCheck = required
                ? permissionEvaluator.assertCanFromCurrentContext(resource, action)
                : permissionEvaluator.canFromCurrentContext(resource, action)
                        .filter(allowed -> !allowed)
                        .flatMap(__ -> Mono.error(TntRoleException.forbidden(resource, action)))
                        .then();

        return permissionCheck.then(Mono.defer(() -> {
            try {
                return (Mono<?>) joinPoint.proceed();
            } catch (Throwable t) {
                return Mono.error(t);
            }
        })).doOnSubscribe(s ->
                log.debug("Permission check: resource={} action={}", resource, action));
    }

    private Flux<?> enforceOnFlux(
            ProceedingJoinPoint joinPoint,
            String resource,
            String action,
            boolean required) {
        Mono<Void> permissionCheck = required
                ? permissionEvaluator.assertCanFromCurrentContext(resource, action)
                : Mono.empty();

        return permissionCheck.thenMany(Flux.defer(() -> {
            try {
                return (Flux<?>) joinPoint.proceed();
            } catch (Throwable t) {
                return Flux.error(t);
            }
        })).doOnSubscribe(s ->
                log.debug("Permission check (Flux): resource={} action={}", resource, action));
    }
}
