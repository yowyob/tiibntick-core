package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private java.util.UUID id;
    private String lastName;
    private String firstName;
    private String email;
    private String phone;
    private String password;
    private String photoCard;

    // Additional Person fields
    private String nationalId;
    private String criminalRecord;
    private Double rating;
    private Integer totalDeliveries;

    // Role based fields
    private String userType; // ADMIN, CLIENT, LIVREUR
    private String role;
    private Boolean isActive;

    // Client specific fields
    private java.util.UUID clientId;
    private String loyaltyStatus;

    // Delivery person specific fields
    private java.util.UUID freelancerId;
}
