package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * The kind of local aggregate a {@link RoleSyncOutboxEntry} refers to via
 * {@link RoleSyncOutboxEntry#aggregateId()} — either a {@link Role} or a
 * {@link UserRoleAssignment}.
 *
 * @author MANFOUO Braun
 */
public enum RoleSyncAggregateType {
    ROLE,
    ASSIGNMENT
}
