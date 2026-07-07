package com.yowyob.tiibntick.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method parameter annotation that marks a {@code UUID} parameter to be populated
 * with the current tenant ID resolved from the Reactor Context.
 *
 * <p>Processed by {@link com.yowyob.tiibntick.common.aop.TenantValidationAspect}.
 * The annotated parameter must be of type {@code java.util.UUID}.
 *
 * <p>Usage:
 * <pre>{@code
 * @TenantScoped
 * public Mono<Mission> findById(
 *         @CurrentTenant UUID tenantId,
 *         UUID missionId) {
 *     return missionRepository.findByIdAndTenantId(missionId, tenantId);
 * }
 * }</pre>
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentTenant {
}
