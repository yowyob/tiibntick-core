package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for delivery person update.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerUpdateRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String password;
    private String commercialName;

    // Sensitive fields
    private String commercialRegister;
    private String logisticsType;
    private String backPhoto;
    private String frontPhoto;

    // Logistics fields
    private String plateNumber;
    private String logisticsClass;
    private String color;
    private Double tankCapacity;
    private Double length;
    private Double width;
    private Double height;
    private String unit;
    private Integer totalSeatNumber;

    // Address fields? Usually separate update, but if included:
    private String street;
    private String city;
    private String district;
    private String country;
    private String description;
}
