package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user registration.
 * A user only needs a name and password to create a delivery need.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationDTO {

    @NotBlank(message = "Le nom est requis")
    private String lastName;

    @NotBlank(message = "Le prénom est requis")
    private String firstName;

    @NotBlank(message = "Le mot de passe est requis")
    private String password;
}
