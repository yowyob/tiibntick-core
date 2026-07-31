package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for local GoFP user registration.
 *
 * <p>Minimal required fields are first/last name and password. Optional
 * {@code email}/{@code phone} are accepted so payloads aligned with login-style
 * schemas do not fail JSON decoding with HTTP 500.
 *
 * @author MANFOUO BRAUN
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRegistrationDTO {

    @NotBlank(message = "Le nom est requis")
    private String lastName;

    @NotBlank(message = "Le prénom est requis")
    private String firstName;

    @NotBlank(message = "Le mot de passe est requis")
    private String password;

    /** Optional — stored locally when provided; otherwise a unique placeholder is generated. */
    private String email;

    /** Optional contact phone. */
    private String phone;
}
