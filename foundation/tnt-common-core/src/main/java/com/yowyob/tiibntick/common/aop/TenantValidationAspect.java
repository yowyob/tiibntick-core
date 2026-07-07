package com.yowyob.tiibntick.common.aop;

import com.yowyob.tiibntick.common.annotation.CurrentTenant;
import com.yowyob.tiibntick.common.annotation.TenantScoped;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import yowyob.comops.api.kernel.application.port.in.CurrentTenantUseCase;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * AOP aspect that enforces tenant context validation for methods annotated
 * with {@link TenantScoped}.
 *
 * <p>Integrates with the Yowyob Kernel's {@link CurrentTenantUseCase} (from
 * {@code RT-comops-kernel-core}) to read the current tenant from the Reactor Context.
 * The Kernel's {@code TenantContextWebFilter} sets the tenant at the HTTP boundary;
 * this aspect validates it is present at the use-case boundary.
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
                .block();
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
