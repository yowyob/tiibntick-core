package com.yowyob.tiibntick.core.incident.application.query;

import java.util.UUID;

/**
 * Identity of the caller driving a read/write on a single incident, used to enforce
 * tenant isolation and actor-level ownership scoping in the application service layer.
 *
 * <p>{@code privileged} reflects whether the caller holds {@code incident:manage}
 * (agency/support/org-admin tier) — privileged callers see/act on every incident within
 * their own tenant; non-privileged callers (freelancer, permanent deliverer, client, relay
 * operator) are restricted to incidents they reported themselves.
 *
 * @author MANFOUO Braun
 */
public record IncidentRequesterContext(UUID actorId, UUID tenantId, boolean privileged) {
}
