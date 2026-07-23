package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * The Kernel-facing operation a {@link RoleSyncOutboxEntry} drives.
 *
 * <p>Deliberately has no {@code UPDATE_ROLE} member: the Kernel's role-controller exposes
 * no PUT/PATCH endpoint for {@code /api/roles/{id}} (confirmed against
 * {@code docs/kernel-api/endpoints.md}). Role edits therefore stay local-only by design —
 * a documented decision, not a gap this outbox is meant to fill.
 *
 * @author MANFOUO Braun
 */
public enum RoleSyncOperation {
    PROVISION_ROLE,
    DELETE_ROLE,
    ASSIGN_ROLE,
    REVOKE_ASSIGNMENT
}
