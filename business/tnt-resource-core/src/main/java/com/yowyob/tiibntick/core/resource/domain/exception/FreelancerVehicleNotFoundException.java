package com.yowyob.tiibntick.core.resource.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)

/**
 * Thrown when a {@code FreelancerVehicle} cannot be found by its ID.
 *
 * @author MANFOUO Braun
 */
public class FreelancerVehicleNotFoundException extends RuntimeException {
    public FreelancerVehicleNotFoundException(UUID vehicleId) {
        super("FreelancerVehicle not found: " + vehicleId);
    }
}
