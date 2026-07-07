package com.yowyob.tiibntick.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a method requires a valid tenant context in the Reactor Context.
 *
 * <p>The {@link com.yowyob.tiibntick.common.aop.TenantValidationAspect} intercepts
 * methods annotated with {@code @TenantScoped} and:
 * <ol>
 *   <li>Verifies that a tenant ID is present in the Reactor Context (set by the
 *       Kernel's {@code TenantContextWebFilter}).</li>
 *   <li>Injects the tenant ID into any parameter annotated with {@link CurrentTenant}.</li>
 * </ol>
 *
 * <p>If the tenant context is missing, a {@code TenantContextMissingException}
 * (from the Kernel) is propagated before the method body executes.
 *
 * <p>Usage:
 * <pre>{@code
 * @TenantScoped
 * public Mono<Mission> findById(@CurrentTenant UUID tenantId, UUID missionId) { ... }
 *
 * // For cross-tenant admin operations, disable ownership validation:
 * @TenantScoped(validateOwnership = false)
 * public Flux<Mission> findAllForAdmin(@CurrentTenant UUID tenantId) { ... }
 * }</pre>
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantScoped {

    /**
     * When {@code true} (default), the aspect attempts to validate that tenant-scoped
     * entity arguments belong to the caller's tenant. Set to {@code false} for
     * cross-tenant administrative operations.
     */
    boolean validateOwnership() default true;
}
