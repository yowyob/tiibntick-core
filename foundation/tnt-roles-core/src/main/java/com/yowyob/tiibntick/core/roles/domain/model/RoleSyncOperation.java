package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * The Kernel-facing operation a {@link RoleSyncOutboxEntry} drives.
 *
 * <p>{@code UPDATE_ROLE} records local permission reconciliation performed at startup by
 * {@code TntRoleInitializationService} when a system role's permission set in the local DB
 * diverges from the canonical definition in {@code TntRole}. The Kernel's role-controller
 * exposes no PUT/PATCH endpoint for {@code /api/roles/{id}} (confirmed against
 * {@code docs/kernel-api/endpoints.md}), so the Kernel sync worker treats this operation
 * as local-only and does not attempt a Kernel HTTP call — the outbox entry serves as an
 * audit record of the reconciliation event rather than a pending Kernel write.
 *
 * @author MANFOUO Braun
 */
public enum RoleSyncOperation {
    PROVISION_ROLE,
    UPDATE_ROLE,
    DELETE_ROLE,
    ASSIGN_ROLE,
    REVOKE_ASSIGNMENT
}
