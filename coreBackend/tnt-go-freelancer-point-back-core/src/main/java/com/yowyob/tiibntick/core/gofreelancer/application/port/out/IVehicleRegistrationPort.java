package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;
import java.util.UUID;

public interface IVehicleRegistrationPort {
    /**
     * Registers the vehicle in the central resource core and returns the generated UUID.
     * 
     * @param tenantId The tenant ID
     * @param organizationId The organization ID (freelancer acting as organization)
     * @param agencyId The agency ID (usually same as org for freelancers)
     * @param registrationNumber Vehicle plate/registration
     * @param brand Vehicle brand
     * @param model Vehicle model
     * @param yearOfManufacture Year
     * @param type Vehicle type (moto, car, etc)
     * @param maxWeightKg Max weight
     * @param maxVolumeM3 Max volume calculated from dimensions
     * @return Mono of the generated core_vehicle_id
     */
    Mono<UUID> registerVehicle(
            UUID tenantId, 
            UUID organizationId, 
            UUID agencyId, 
            String registrationNumber, 
            String brand, 
            String model, 
            int yearOfManufacture, 
            String type, 
            double maxWeightKg, 
            double maxVolumeM3
    );
}
