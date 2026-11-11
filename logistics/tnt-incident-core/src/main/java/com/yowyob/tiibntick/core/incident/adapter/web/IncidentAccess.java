package com.yowyob.tiibntick.core.incident.adapter.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.incident.application.query.IncidentRequesterContext;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;

import java.util.UUID;

/**
 * Authorization rules shared by the incident controllers.
 *
 * <p>Seeing an incident (reporter or {@code incident:manage}) is not the right to run it: triage, resolution,
 * closing, replacement-driver assignment, agency handling and inter-agency cooperation are management actions and
 * need {@code incident:manage}. A reporter may only feed their own case (evidence, escalation, cancellation,
 * handover confirmation) and always as themselves.
 */
final class IncidentAccess {

    private IncidentAccess() {}

    static boolean canManage(TntUserIdentity identity) {
        return identity.hasPermission("incident", "manage");
    }

    static IncidentRequesterContext contextOf(TntUserIdentity identity) {
        return new IncidentRequesterContext(identity.actorId(), identity.tenantId(), canManage(identity));
    }

    /** Throws 403 unless the caller holds {@code incident:manage}. */
    static void requireManage(TntUserIdentity identity) {
        if (!canManage(identity)) {
            throw TntRoleException.forbidden("incident", "manage");
        }
    }

    /** A non-manager can only name themselves as the acting actor. */
    static void requireSelfUnlessManager(TntUserIdentity identity, UUID actorIdInPayload) {
        if (!canManage(identity) && (actorIdInPayload == null || !actorIdInPayload.equals(identity.actorId()))) {
            throw TntRoleException.forbidden("incident", "manage");
        }
    }
}
