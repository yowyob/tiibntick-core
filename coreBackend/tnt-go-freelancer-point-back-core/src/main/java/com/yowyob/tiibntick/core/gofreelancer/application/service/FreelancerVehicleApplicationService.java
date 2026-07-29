package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RegisterFreelancerVehicleDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IVehicleRegistrationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerVehicleApplicationService {

    private final IVehicleRegistrationPort vehicleRegistrationPort;
    private final FreelancerVehicleRepository freelancerVehicleRepository;

    @Transactional
    public Mono<FreelancerVehicle> registerVehicle(UUID tenantId, UUID freelancerId, RegisterFreelancerVehicleDTO dto) {
        log.info("Registering vehicle for freelancer {}", freelancerId);

        // 1. Calculate the volume in cubic meters based on form dimensions and unit
        double maxVolumeM3 = calculateVolumeInM3(
                dto.getTrunkLength(),
                dto.getTrunkWidth(),
                dto.getTrunkHeight(),
                dto.getTrunkDimensionUnit()
        );

        // We assume freelancer acts as their own organization/agency for now
        UUID organizationId = freelancerId; 
        UUID agencyId = freelancerId;

        // 2. Register the logistical capabilities in the Core (tnt-resource-core)
        return vehicleRegistrationPort.registerVehicle(
                tenantId,
                organizationId,
                agencyId,
                dto.getRegistrationNumber(),
                dto.getBrand(),
                dto.getModel(),
                dto.getYearOfManufacture() != null ? dto.getYearOfManufacture() : 2020,
                dto.getType(),
                dto.getMaxWeightKg() != null ? dto.getMaxWeightKg() : 0.0,
                maxVolumeM3
        ).flatMap(coreVehicleId -> {
            
            // 3. Save the UI/visual extensions locally in Go-Freelancer
            FreelancerVehicle localVehicle = FreelancerVehicle.builder()
                    .id(UUID.randomUUID())
                    .freelancerId(freelancerId)
                    .coreVehicleId(coreVehicleId)
                    .frontPhotoUrl(dto.getFrontPhotoUrl())
                    .backPhotoUrl(dto.getBackPhotoUrl())
                    .colorHex(dto.getColorHex())
                    .trunkLength(dto.getTrunkLength())
                    .trunkWidth(dto.getTrunkWidth())
                    .trunkHeight(dto.getTrunkHeight())
                    .trunkDimensionUnit(dto.getTrunkDimensionUnit())
                    .build();

            return freelancerVehicleRepository.save(localVehicle);
        });
    }

    /**
     * Converts form dimensions to cubic meters based on the provided unit.
     */
    public double calculateVolumeInM3(Double length, Double width, Double height, String unit) {
        if (length == null || width == null || height == null) {
            return 0.0;
        }

        double volume = length * width * height;
        if (unit == null || unit.isBlank()) {
            return volume; // Fallback assumes meters
        }

        return switch (unit.trim().toLowerCase()) {
            case "cm" -> volume / 1_000_000.0;          // Centimeters
            case "mm" -> volume / 1_000_000_000.0;      // Millimeters
            case "m"  -> volume;                        // Meters
            case "in", "inch" -> volume * 0.000016387;  // Inches
            default -> volume;
        };
    }
}
