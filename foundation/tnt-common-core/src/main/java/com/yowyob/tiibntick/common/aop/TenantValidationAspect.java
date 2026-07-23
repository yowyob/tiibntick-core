package com.yowyob.tiibntick.common.aop;

import com.yowyob.tiibntick.common.annotation.CurrentTenant;
import com.yowyob.tiibntick.common.annotation.TenantScoped;
import com.yowyob.tiibntick.common.tenant.CurrentTenantUseCase;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.UUID;

/**
 * AOP aspect that enforces tenant context validation for methods annotated
 * with {@link TenantScoped}.
 *
 * <p>Integrates with {@link CurrentTenantUseCase} to read the current tenant from the
 * Reactor Context. TiiBnTick's own JWT authentication filter sets the tenant at the
 * HTTP boundary; this aspect validates it is present at the use-case boundary.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Validate that a tenant ID is present in the Reactor Context.</li>
 *   <li>Inject it into parameters annotated with {@link CurrentTenant}.</li>
 * </ol>
 *
 * <p>For reactive methods, validation happens inside the reactive chain so that
 * error signals propagate correctly to the caller.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
@Aspect
@Component
@Slf4j
public class TenantValidationAspect {

    /**
     * Chantier D · Audit n°6 · S14 / Audit n°1 · A3 — the blocking fallback branch below
     * used to call {@code .block()} with no timeout: if {@link CurrentTenantUseCase} ever
     * failed to complete (Reactor Context never populated, upstream hang), whatever thread
     * invoked the advised method — potentially a Netty event-loop thread, since this aspect
     * has no way to know its caller's threading context ahead of time — would hang
     * indefinitely. Bounded instead, so a stuck tenant lookup surfaces as a fast, explicit
     * failure.
     */
    private static final Duration BLOCKING_PROCEED_TIMEOUT = Duration.ofSeconds(5);

    private final CurrentTenantUseCase currentTenantUseCase;

    public TenantValidationAspect(CurrentTenantUseCase currentTenantUseCase) {
        this.currentTenantUseCase = currentTenantUseCase;
    }

    @Around("@annotation(tenantScoped)")
    public Object validateTenant(ProceedingJoinPoint joinPoint, TenantScoped tenantScoped)
            throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return currentTenantUseCase.currentTenant()
                .flatMap(ctx -> proceedWithTenantMono(joinPoint, method, ctx.tenantId()));
        } else if (Flux.class.isAssignableFrom(returnType)) {
            return currentTenantUseCase.currentTenant()
                .flatMapMany(ctx -> proceedWithTenantFlux(joinPoint, method, ctx.tenantId()));
        } else {
            // For blocking methods: not ideal in reactive context, but supported for tests/batch
            // (no @TenantScoped method in the codebase is non-reactive today, but this branch
            // must stay safe for whenever one is added).
            return currentTenantUseCase.currentTenant()
                .map(ctx -> {
                    try {
                        Object[] args = joinPoint.getArgs().clone();
                        injectCurrentTenant(args, method, ctx.tenantId());
                        return joinPoint.proceed(args);
                    } catch (Throwable e) {
                        throw new RuntimeException("Error in @TenantScoped blocking method", e);
                    }
                })
                .block(BLOCKING_PROCEED_TIMEOUT);
        }
    }

    // ─── Reactive proceed helpers ─────────────────────────────────────────

    private Mono<?> proceedWithTenantMono(ProceedingJoinPoint joinPoint, Method method, UUID tenantId) {
        try {
            Object[] args = joinPoint.getArgs().clone();
            injectCurrentTenant(args, method, tenantId);
            return (Mono<?>) joinPoint.proceed(args);
        } catch (Throwable e) {
            return Mono.error(e);
        }
    }

    private Flux<?> proceedWithTenantFlux(ProceedingJoinPoint joinPoint, Method method, UUID tenantId) {
        try {
            Object[] args = joinPoint.getArgs().clone();
            injectCurrentTenant(args, method, tenantId);
            return (Flux<?>) joinPoint.proceed(args);
        } catch (Throwable e) {
            return Flux.error(e);
        }
    }

    // ─── Injection helpers ────────────────────────────────────────────────

    private void injectCurrentTenant(Object[] args, Method method, UUID tenantId) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(CurrentTenant.class)) {
                if (!UUID.class.isAssignableFrom(params[i].getType())) {
                    throw new IllegalStateException(
                        "@CurrentTenant parameter must be of type java.util.UUID, " +
                        "found: " + params[i].getType().getName() +
                        " in method: " + method.getName());
                }
                args[i] = tenantId;
            }
        }
    }
}
