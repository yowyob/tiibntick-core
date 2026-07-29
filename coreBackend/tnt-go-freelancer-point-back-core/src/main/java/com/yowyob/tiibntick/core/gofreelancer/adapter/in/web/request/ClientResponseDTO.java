package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Data Transfer Object for client response.
 * Contains complete client information with person details.
 *
 * @author Kengfack Lagrange
 * @date 18/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientResponseDTO {

    private UUID id;
    private UUID personId;
    private String lastName;
    private String firstName;
    private String phone;
    private String email;
    private String nationalId;
    private String photoCard;
    private String criminalRecord;
    private String profilePhoto;
    private Double rating;
    private Integer totalDeliveries;
    private String loyaltyStatus;
    private String status;
}