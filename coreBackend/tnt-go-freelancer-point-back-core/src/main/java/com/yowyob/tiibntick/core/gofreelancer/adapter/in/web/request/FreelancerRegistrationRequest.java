package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for delivery person registration.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerRegistrationRequest {
    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String nationalId;
    private String photoCard;
    private String commercialRegister;
    private String commercialName;
    private String nui;
    private String nuiPhoto;
    private String cniRecto;
    private String cniVerso;
    private Double commissionRate;
    private String siret;

    @NotBlank(message = "Password is required")
    private String password;

    // Logistics fields
    private String plateNumber;
    private String logisticsType; // Enum value
    private String logisticsClass; // Enum value
    private String backPhoto;
    private String frontPhoto;
    private String storefrontPhoto;
    private Double tankCapacity;
    private Double length;
    private Double width;
    private Double height;
    private String unit;
    private Integer totalSeatNumber;
    private String color;
    private java.util.List<OpeningHoursDTO> openingHours;

    // Address fields
    private String street;
    private String city;
    private String district;
    private String country;
    private String description;
}
