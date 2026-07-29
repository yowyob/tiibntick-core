package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for returning subscription details including delivery person info.
 *
 * @author TiiBnTickTeam
 * @date 23/02/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponseDTO {
    private UUID subscriptionId;
    private UUID freelancerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Double rating;
    private String status;
    private Instant createdAt;
}
