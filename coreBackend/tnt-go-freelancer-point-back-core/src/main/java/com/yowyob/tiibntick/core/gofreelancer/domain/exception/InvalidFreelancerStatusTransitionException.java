package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;

/**
 * Thrown when a requested FreelancerStatus transition is not allowed by the lifecycle rules.
 * Maps to HTTP 409 via GlobalExceptionHandler's IllegalStateException handler.
 */
public class InvalidFreelancerStatusTransitionException extends IllegalStateException {

    public InvalidFreelancerStatusTransitionException(FreelancerStatus from, FreelancerStatus to) {
        super("Transition " + from + " → " + to + " is not allowed");
    }
}
