package com.yowyob.tiibntick.core.roles.application.port.out;

import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Decision D-176: the agency/organisation roles the Core itself assigned to a user in a tenant, as canonical entries
 * ({@code AGENCY_MANAGER}, {@code AGENCY_MANAGER#AGENCY:<id>}…, see
 * {@link com.yowyob.tiibntick.core.roles.domain.model.ScopedTokenRoles}).
 */
public interface IScopedRoleAssignmentsPort {

    Flux<String> scopedRoleEntries(UUID tenantId, UUID userId);
}
