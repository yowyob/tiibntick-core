package com.yowyob.tiibntick.core.actor.domain.exception;

import java.util.UUID;

/**
 * Exception thrown when a FreelancerOrganization link operation fails.
 *
 * <p>Examples of failure scenarios:
 * <ul>
 *   <li>Attempting to link an actor who is already linked to another org.</li>
 *   <li>Attempting to unlink an actor who has no org link.</li>
 *   <li>Attempting to link with a null or invalid org ID.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class FreelancerOrgLinkException extends RuntimeException {

    private final UUID actorId;
    private final UUID orgId;

    public FreelancerOrgLinkException(UUID actorId, UUID orgId, String message) {
        super(message);
        this.actorId = actorId;
        this.orgId = orgId;
    }

    public FreelancerOrgLinkException(UUID actorId, UUID orgId, String message, Throwable cause) {
        super(message, cause);
        this.actorId = actorId;
        this.orgId = orgId;
    }

    public UUID actorId() { return actorId; }
    public UUID orgId() { return orgId; }
}
