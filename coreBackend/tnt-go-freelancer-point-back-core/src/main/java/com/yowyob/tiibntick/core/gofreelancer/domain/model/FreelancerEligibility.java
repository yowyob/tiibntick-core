package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Storage row of what a delivery person accepts to carry and when. JSON columns are plain text so
 * the row does not depend on a driver-specific JSON codec; {@code FreelancerEligibilityService}
 * owns their (de)serialisation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("freelancer_eligibility")
public class FreelancerEligibility {

    @Id
    @Column("id")
    private UUID id;

    @Column("delivery_person_id")
    private UUID deliveryPersonId;

    @Column("availability")
    private String availabilityJson;

    @Column("accepted_packages")
    private String acceptedPackagesJson;

    @Column("min_weight_kg")
    private Double minWeightKg;

    @Column("max_weight_kg")
    private Double maxWeightKg;
}
