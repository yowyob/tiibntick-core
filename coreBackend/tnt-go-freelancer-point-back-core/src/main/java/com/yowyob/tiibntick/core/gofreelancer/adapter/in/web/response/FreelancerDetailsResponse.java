package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for delivery person details.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerDetailsResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String status;
    private String commercialName;
    private String nuiNumber; // Taxpayer Number / NINE
    @JsonProperty("nuiPhoto")
    private String nuiPhoto; // Path to NIU photo

    private String street;
    private String city;

    // Personal Info
    private String nationalId;
    private String photoCard;
    private String cniRecto;
    private String cniVerso;
    private Boolean idCardVerified; // Derived or stored? Assuming manual verification for now

    // Vehicle Info
    private String vehicleType; // From LogisticsType
    private String vehicleBrand; // Placeholder/Future
    private String vehicleModel; // Placeholder/Future
    private String vehicleRegNumber; // Plate number
    private String vehicleColor;
    private String vehicleFrontPhoto;
    private String vehicleBackPhoto;
    private Double trunkLength;
    private Double trunkWidth;
    private Double trunkHeight;
    private String trunkDimensionUnit;
    private Boolean vehicleRegVerified; // Placeholder
    private Boolean insuranceVerified; // Placeholder

    // Meta
    private String createdAt;
    private String updatedAt;
}
