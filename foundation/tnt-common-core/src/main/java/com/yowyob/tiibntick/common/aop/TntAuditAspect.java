package com.yowyob.tiibntick.common.aop;

import com.yowyob.tiibntick.common.annotation.Audited;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * AOP aspect that intercepts methods annotated with {@link Audited} and produces
 * a TiiBnTick audit entry via {@link TntAuditEventPort}.
 *
 * <p>Supports three method return types:
 * <ul>
 *   <li>{@link Mono} — audit recorded as a side-effect after the Mono terminates</li>
 *   <li>{@link Flux} — audit recorded on completion or error</li>
 *   <li>Blocking — audit recorded synchronously after method returns</li>
 * </ul>
 *
 * <p>If no {@link TntAuditEventPort} bean is available, falls back to structured logging.
 * This makes the aspect safe to use in test contexts without a full Spring container.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
@Aspect
@Component("tntAuditAspect")
@Slf4j
public class TntAuditAspect {

    private final Optional<TntAuditEventPort> auditPort;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Autowired
    public TntAuditAspect(@Autowired(required = false) TntAuditEventPort auditPort) {
        this.auditPort = Optional.ofNullable(auditPort);
    }

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return auditMono(joinPoint, audited, method);
        } else if (Flux.class.isAssignableFrom(returnType)) {
            return auditFlux(joinPoint, audited, method);
        } else {
            return auditBlocking(joinPoint, audited, method);
        }
    }

    // ─── Reactive support ────────────────────────────────────────────────

    private Mono<?> auditMono(ProceedingJoinPoint joinPoint, Audited audited, Method method)
            throws Throwable {
        Mono<?> upstream = (Mono<?>) joinPoint.proceed();
        return upstream
            .doOnSuccess(r -> record(audited, method, joinPoint.getArgs(), "SUCCESS", null))
            .doOnError(e -> {
                if (audited.auditOnFailure()) {
                    record(audited, method, joinPoint.getArgs(), "FAILURE", e.getMessage());
                }
            });
    }

    private Flux<?> auditFlux(ProceedingJoinPoint joinPoint, Audited audited, Method method)
            throws Throwable {
        Flux<?> upstream = (Flux<?>) joinPoint.proceed();
        return upstream
            .doOnComplete(() -> record(audited, method, joinPoint.getArgs(), "SUCCESS", null))
            .doOnError(e -> {
                if (audited.auditOnFailure()) {
                    record(audited, method, joinPoint.getArgs(), "FAILURE", e.getMessage());
                }
            });
    }

    private Object auditBlocking(ProceedingJoinPoint joinPoint, Audited audited, Method method)
            throws Throwable {
        try {
            Object result = joinPoint.proceed();
            record(audited, method, joinPoint.getArgs(), "SUCCESS", null);
            return result;
        } catch (Throwable e) {
            if (audited.auditOnFailure()) {
                record(audited, method, joinPoint.getArgs(), "FAILURE", e.getMessage());
            }
            throw e;
        }
    }

    // ─── Recording logic ─────────────────────────────────────────────────

    private void record(Audited audited, Method method, Object[] args,
                        String outcome, String errorMessage) {
        try {
            String aggregateId = resolveAggregateId(audited.aggregateIdExpression(), method, args);

            if (auditPort.isPresent()) {
                auditPort.get().record(
                    null, // tenantId resolved inside the port from Reactor Context
                    audited.action(),
                    audited.aggregateType(),
                    aggregateId,
                    outcome,
                    errorMessage,
                    null  // correlationId resolved inside the port
                ).subscribe(
                    v -> {},
                    err -> log.warn("[AUDIT-ASPECT] Failed to persist audit for action={}: {}",
                        audited.action(), err.getMessage())
                );
            } else {
                // Fallback: structured log when no audit port is wired
                log.info("[AUDIT] action={} aggregateType={} aggregateId={} outcome={}",
                    audited.action(), audited.aggregateType(), aggregateId, outcome);
                if (errorMessage != null) {
                    log.info("[AUDIT] error={}", errorMessage);
                }
            }
        } catch (Exception e) {
            log.warn("[AUDIT-ASPECT] Error preparing audit for action={}: {}",
                audited.action(), e.getMessage());
        }
    }

    private String resolveAggregateId(String spelExpression, Method method, Object[] args) {
        if (spelExpression == null || spelExpression.isBlank()) {
            return null;
        }
        try {
            EvaluationContext ctx = new StandardEvaluationContext();
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                ctx.setVariable(params[i].getName(), args[i]);
            }
            Expression expr = spelParser.parseExpression(spelExpression);
            Object value = expr.getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("[AUDIT-ASPECT] Could not evaluate SpEL '{}': {}", spelExpression, e.getMessage());
            return null;
        }
    }
}
