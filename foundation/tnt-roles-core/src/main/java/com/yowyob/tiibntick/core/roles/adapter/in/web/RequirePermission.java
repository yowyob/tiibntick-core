package com.yowyob.tiibntick.core.roles.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative permission enforcement annotation for TiiBnTick service and controller methods.
 *
 * <p>Applied to methods returning {@code Mono<?>} or {@code Flux<?>}, the
 * {@link TntPermissionAspect} will prepend a permission check using the current
 * authenticated user's permissions from the Spring Security reactive context.
 * If the check fails, the reactive chain emits a
 * {@link com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException#forbidden(String, String)}.
 *
 * <p>Usage examples:
 * <pre>{@code
 * @RequirePermission(resource = "mission", action = "create")
 * public Mono<Mission> createMission(CreateMissionCommand cmd) { ... }
 *
 * @RequirePermission(resource = "report", action = "export")
 * public Flux<ReportLine> exportReport(UUID tenantId) { ... }
 *
 * // With declared constant from TntPermission:
 * @RequirePermission(resource = "billing", action = "write")
 * public Mono<Invoice> issueInvoice(IssueInvoiceCommand cmd) { ... }
 * }</pre>
 *
 * <p>The annotation is evaluated against the permissions in the current
 * reactive security context (fast path from JWT authorities) without an
 * additional DB lookup.
 *
 * @author MANFOUO Braun
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * The permission resource (e.g. "mission", "billing", "report").
     * Must correspond to a {@link com.yowyob.tiibntick.core.roles.domain.model.TntPermission} prefix.
     */
    String resource();

    /**
     * The permission action (e.g. "create", "read", "export").
     * Must correspond to a {@link com.yowyob.tiibntick.core.roles.domain.model.TntPermission} suffix.
     */
    String action();

    /**
     * When true (default), the method call fails with a {@code TntRoleException.missingContext()}
     * if no authenticated security context is present.
     * When false, the method is allowed to proceed without authentication.
     */
    boolean required() default true;
}
