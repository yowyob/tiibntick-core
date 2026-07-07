package com.yowyob.tiibntick.core.roles.application.port.in;

import com.yowyob.tiibntick.core.roles.domain.model.TntPermissionContext;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Primary (inbound) port: reactive permission evaluation DSL for TiiBnTick.
 *
 * <p>All operations delegate to the Kernel's {@code ReactivePermissionResolver}
 * ({@code RT-comops-roles-core}) which handles DB lookup and Redis caching.
 * This port adds TiiBnTick-specific semantics: scoped suffix matching,
 * wildcard expansion, and reactive error propagation.
 *
 * @author MANFOUO Braun
 */
public interface CheckPermissionUseCase {

    /**
     * Returns true if the user has the given permission within the provided context.
     * Checks: exact match, resource wildcard ({@code resource:*}), global wildcard ({@code *}),
     * and scoped variants ({@code resource:action#AGENCY:<agencyId>}).
     *
     * @param ctx    identity context (userId + tenantId mandatory)
     * @param resource permission resource (e.g. "mission")
     * @param action   permission action (e.g. "create")
     */
    Mono<Boolean> can(TntPermissionContext ctx, String resource, String action);

    /**
     * Inverse of {@link #can}.
     */
    Mono<Boolean> cannot(TntPermissionContext ctx, String resource, String action);

    /**
     * Asserts the user has the permission, emitting
     * {@link com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException#forbidden(String, String)}
     * if not. Suitable for use in reactive service chains.
     */
    Mono<Void> assertCan(TntPermissionContext ctx, String resource, String action);

    /**
     * Resolves the full permission set for the user (delegated to Kernel resolver + Redis cache).
     */
    Mono<Set<String>> resolvePermissions(TntPermissionContext ctx);

    /**
     * Returns true if the user holds the given TiiBnTick role code.
     *
     * @param ctx      identity context
     * @param roleCode TiiBnTick role code (e.g. "AGENCY_MANAGER")
     */
    Mono<Boolean> hasRole(TntPermissionContext ctx, String roleCode);
}
