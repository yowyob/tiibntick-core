package com.yowyob.tiibntick.core.roles.application.port.in;

import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Decision D-176: which agency/organisation roles of a Kernel token the Core confirms for this user in this tenant.
 * Used by the JWT converter, the single place where token roles become authorities.
 */
public interface ConfirmScopedRolesUseCase {

    /** Canonical confirmed entries; empty when the Core assigned no agency/organisation role here. */
    Mono<Set<String>> confirmedScopedRoles(UUID tenantId, UUID userId);
}
