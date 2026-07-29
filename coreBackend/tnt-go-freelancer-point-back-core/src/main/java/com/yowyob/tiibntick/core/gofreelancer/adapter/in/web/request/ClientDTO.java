package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for creating a new client.
 * Contains both person and client information.
 *
 * @author Kemeugne Michèle
 * @date 18/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    // Person fields
    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "National ID is required")
    private String nationalId;

    @NotBlank(message = "Photo card is required")
    private String photoCard;

    private String criminalRecord;

    private String profilePhoto;

    // Client specific field
    @NotBlank(message = "Loyalty status is required")
    private String loyaltyStatus;
}