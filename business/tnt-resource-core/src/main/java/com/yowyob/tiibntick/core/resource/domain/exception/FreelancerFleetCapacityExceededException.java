package com.yowyob.tiibntick.core.resource.domain.exception;

import java.util.UUID;

/**
 * Thrown when adding a vehicle would exceed the maximum fleet size
 * for a FreelancerOrganization ({@value MAX_VEHICLES} vehicles).
 *
 * @author MANFOUO Braun
 */
public class FreelancerFleetCapacityExceededException extends RuntimeException {
    public static final int MAX_VEHICLES = 3;

    public FreelancerFleetCapacityExceededException(UUID ownerOrgId) {
        super("FreelancerOrganization " + ownerOrgId + " has reached the maximum fleet size of "
                + MAX_VEHICLES + " vehicles.");
    }
}
