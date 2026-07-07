package com.yowyob.tiibntick.core.resource.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command to assign a FreelancerOrg vehicle to an active delivery mission.
 *
 * <p>Published as a {@code FreelancerVehicleAssignedToMissionEvent} to
 * {@code tnt.vehicle.assigned_to_mission} after successful assignment.
 *
 * @author MANFOUO Braun
 */
public record AssignFreelancerVehicleToMissionCommand(
        @NotNull UUID vehicleId,
        @NotNull UUID ownerOrgId,
        @NotBlank String missionId
) {}
