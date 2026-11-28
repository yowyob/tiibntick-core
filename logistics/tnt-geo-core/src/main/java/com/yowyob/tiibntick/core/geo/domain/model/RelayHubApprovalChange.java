package com.yowyob.tiibntick.core.geo.domain.model;

import java.util.UUID;

/**
 * An administrator changed the approval of a relay hub (D-138): approval, rejection, suspension or reactivation.
 * The revocation has its own record, {@link RelayHubRevocation}.
 *
 * @param hub      the hub after the change
 * @param previous its approval status before
 * @param reason   the administrator's reason when one was given (rejection, suspension), else {@code null}
 * @param actorId  the administrator, {@code null} when unknown
 */
public record RelayHubApprovalChange(RelayHub hub, HubApprovalStatus previous, String reason, UUID actorId) {
}
