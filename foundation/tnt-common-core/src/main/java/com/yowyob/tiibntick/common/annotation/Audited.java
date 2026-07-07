package com.yowyob.tiibntick.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose execution must produce a TiiBnTick audit entry.
 *
 * <p>The {@link com.yowyob.tiibntick.common.aop.TntAuditAspect} intercepts methods
 * annotated with {@code @Audited} and records the action, actor, outcome, and context.
 * Supports reactive ({@code Mono<T>}, {@code Flux<T>}) and blocking return types.
 *
 * <p>Usage:
 * <pre>{@code
 * @Audited(action = "MISSION_CREATED", aggregateType = "Mission")
 * public Mono<Mission> createMission(CreateMissionCommand command) { ... }
 *
 * @Audited(
 *     action = "PACKAGE_STATUS_UPDATED",
 *     aggregateType = "Package",
 *     aggregateIdExpression = "#command.packageId"
 * )
 * public Mono<Package> updatePackageStatus(UpdateStatusCommand command) { ... }
 * }</pre>
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /**
     * Action code stored in the audit log. Convention: UPPER_SNAKE_CASE, domain-prefixed.
     * Examples: {@code "MISSION_CREATED"}, {@code "PACKAGE_STATUS_UPDATED"}, {@code "HUB_DEPOSIT_REGISTERED"}.
     */
    String action();

    /**
     * The DDD aggregate type being operated on.
     * Examples: {@code "Mission"}, {@code "DeliveryPackage"}, {@code "Invoice"}.
     */
    String aggregateType() default "";

    /**
     * SpEL expression evaluated against the method arguments to extract the aggregate ID.
     * Examples: {@code "#command.missionId"}, {@code "#id.toString()"}.
     * Leave empty when the aggregate ID is not available at call time (e.g., on creation).
     */
    String aggregateIdExpression() default "";

    /**
     * When {@code true} (default), the audit entry is also recorded when the method
     * fails or the reactive chain terminates with an error. Outcome will be {@code FAILURE}.
     */
    boolean auditOnFailure() default true;
}
