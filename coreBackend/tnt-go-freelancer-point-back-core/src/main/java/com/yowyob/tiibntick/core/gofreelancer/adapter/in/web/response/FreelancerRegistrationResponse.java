package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for delivery person registration.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerRegistrationResponse {
    private UUID freelancerId;
    private String status;
}
