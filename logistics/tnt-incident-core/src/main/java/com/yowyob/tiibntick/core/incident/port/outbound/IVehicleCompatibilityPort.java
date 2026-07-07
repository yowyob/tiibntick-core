package com.yowyob.tiibntick.core.incident.port.outbound;

import com.yowyob.tiibntick.core.incident.domain.model.VehicleInfo;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: query vehicle information and availability from tnt-resource-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IVehicleCompatibilityPort {
    Mono<VehicleInfo> getVehicleInfo(UUID vehicleId);
    Mono<Boolean> isVehicleAvailable(UUID vehicleId);

    /**
     * Places a vehicle in incident substitution status for inter-agency lending.
     */
    Mono<Void> placeVehicleInSubstitution(UUID vehicleId, UUID borrowingAgencyId);

    /**
     * Releases a vehicle from incident substitution, returning it to AVAILABLE.
     */
    Mono<Void> releaseVehicleFromSubstitution(UUID vehicleId);
}
