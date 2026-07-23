package com.yowyob.tiibntick.core.roles.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary (inbound) port: revokes a previously granted {@code UserRoleAssignment}.
 *
 * <p>Local RBAC persistence (Chantier D · Audit n°6 · S5): the implementation deletes the
 * local assignment row and — when the assignment carries a recorded Kernel-side id —
 * enqueues a {@code RoleSyncOutboxEntry} for asynchronous Kernel-side revocation. It does
 * not call the Kernel directly in-request.
 *
 * @author MANFOUO Braun
 */
public interface RevokeTntRoleUseCase {

    /**
     * Revokes the assignment identified by {@code assignmentId} under {@code tenantId}.
     *
     * @param tenantId     tenant the assignment belongs to
     * @param assignmentId local {@code UserRoleAssignment} id to revoke
     * @return completes once the local row is deleted (and, when applicable, the Kernel-facing
     *         revocation is enqueued); errors if no such assignment exists
     */
    Mono<Void> revokeAssignment(UUID tenantId, UUID assignmentId);
}
