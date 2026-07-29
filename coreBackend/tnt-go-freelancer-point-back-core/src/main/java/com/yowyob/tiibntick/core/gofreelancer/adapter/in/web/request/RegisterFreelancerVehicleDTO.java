package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFreelancerVehicleDTO {
    private String registrationNumber;
    private String brand;
    private String model;
    private Integer yearOfManufacture;
    private String type;
    private Double maxWeightKg;

    // Visuals and form-specific dimensions
    private String frontPhotoUrl;
    private String backPhotoUrl;
    private String colorHex;
    private Double trunkLength;
    private Double trunkWidth;
    private Double trunkHeight;
    private String trunkDimensionUnit;
}
