package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for assigning a delivery person to an announcement.
 *
 * @author TiiBnTickTeam
 * @date 23/02/2026
 */
@Data
@NoArgsConstructor
public class AssignFreelancerRequestDTO {
    private UUID freelancerId;
}
