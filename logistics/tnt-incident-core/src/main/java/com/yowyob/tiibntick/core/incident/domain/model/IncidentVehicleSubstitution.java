package com.yowyob.tiibntick.core.incident.domain.model;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Vehicle replacement within the same agency or across agencies during an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentVehicleSubstitution {

    private UUID id;
    private UUID incidentId;
    private UUID originalVehicleId;
    private UUID substituteVehicleId;
    private UUID substituteAgencyId;
    private boolean interAgencySubstitution;
    private Instant substitutedAt;
    private boolean capacityVerified;
    private boolean categoryCompatible;
    private boolean driverTransferred;

    public static IncidentVehicleSubstitution create(UUID incidentId, UUID originalVehicleId,
                                                      UUID substituteVehicleId, UUID substituteAgencyId,
                                                      boolean interAgency, boolean capacityOk,
                                                      boolean categoryOk) {
        return IncidentVehicleSubstitution.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .originalVehicleId(originalVehicleId)
                .substituteVehicleId(substituteVehicleId)
                .substituteAgencyId(substituteAgencyId)
                .interAgencySubstitution(interAgency)
                .substitutedAt(Instant.now())
                .capacityVerified(capacityOk)
                .categoryCompatible(categoryOk)
                .driverTransferred(false)
                .build();
    }

    public IncidentVehicleSubstitution markDriverTransferred() {
        return toBuilder().driverTransferred(true).build();
    }

    public IncidentVehicleSubstitutionBuilder toBuilder() {
        return IncidentVehicleSubstitution.builder().id(id).incidentId(incidentId)
                .originalVehicleId(originalVehicleId).substituteVehicleId(substituteVehicleId)
                .substituteAgencyId(substituteAgencyId).interAgencySubstitution(interAgencySubstitution)
                .substitutedAt(substitutedAt).capacityVerified(capacityVerified)
                .categoryCompatible(categoryCompatible).driverTransferred(driverTransferred);
    }
}
