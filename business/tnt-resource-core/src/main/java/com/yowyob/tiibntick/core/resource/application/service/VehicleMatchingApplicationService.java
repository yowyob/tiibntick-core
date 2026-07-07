package com.yowyob.tiibntick.core.resource.application.service;

import com.yowyob.tiibntick.core.resource.application.port.in.FindBestVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.FindBestVehicleForMissionUseCase;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleRepository;
import com.yowyob.tiibntick.core.resource.domain.exception.NoAvailableVehicleException;
import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Application service responsible for matching a vehicle to a mission based on
 * capacity requirements. Selection strategy: prefer the smallest sufficient vehicle
 * to minimise fuel consumption and per-km cost.
 *
 * Used by tnt-delivery-core when creating and assigning missions.
 *
 * @author MANFOUO Braun.
 */
@Service
public class VehicleMatchingApplicationService implements FindBestVehicleForMissionUseCase {

    private final VehicleRepository vehicleRepository;

    public VehicleMatchingApplicationService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Mono<Vehicle> findBestVehicle(FindBestVehicleCommand cmd) {
        List<java.util.UUID> excludes = cmd.excludeVehicleIds() != null
                ? cmd.excludeVehicleIds() : List.of();

        return vehicleRepository.findAvailableByAgency(cmd.tenantId(), cmd.agencyId())
                .filter(v -> !excludes.contains(v.id()))
                .filter(v -> v.canCarry(cmd.requiredWeightKg(), cmd.requiredVolumeM3()))
                // Select vehicle with smallest surplus capacity (best-fit strategy)
                .sort(Comparator.comparingDouble(v ->
                        (v.capacity().maxWeightKg() - cmd.requiredWeightKg())
                                + (v.capacity().maxVolumeM3() - cmd.requiredVolumeM3()) * 100))
                .next()
                .switchIfEmpty(Mono.error(new NoAvailableVehicleException(
                        cmd.agencyId(), cmd.requiredWeightKg(), cmd.requiredVolumeM3())));
    }
}
