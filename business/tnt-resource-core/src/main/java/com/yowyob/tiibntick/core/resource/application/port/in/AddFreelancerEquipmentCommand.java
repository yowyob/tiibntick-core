package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.OwnershipType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command to add a new piece of specialized equipment to a FreelancerOrganization.
 *
 * @author MANFOUO Braun
 */
public record AddFreelancerEquipmentCommand(
        /** UUID of the owning FreelancerOrganization. Integration key — no join. */
        @NotNull UUID ownerOrgId,

        /**
         * Type of specialized equipment.
         * (REFRIGERATED_BOX, CARGO_BAG, WATERPROOF_COVER, TRACKING_BEACON, etc.)
         */
        @NotNull EquipmentType type,

        /** Free-text description of the equipment. */
        String description,

        /** Maximum payload capacity in kg (null for non-carrying equipment). */
        Double maxCapacityKg,

        /** Whether the equipment is owned or rented by the FreelancerOrg. */
        OwnershipType ownedOrRented
) {}
