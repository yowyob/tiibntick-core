package com.yowyob.tiibntick.core.resource.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown when no vehicle in the agency fleet can satisfy the given mission requirements.
 * @author MANFOUO Braun.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoAvailableVehicleException extends RuntimeException {

    public NoAvailableVehicleException(UUID agencyId, double weightKg, double volumeM3) {
        super(String.format(
                "No available vehicle found in agency %s for load: %.2f kg / %.3f m³",
                agencyId, weightKg, volumeM3));
    }
}
