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
    /** Preferred: select by delivery-core response id. */
    private UUID responseId;
    /** Legacy: resolve response by freelancer id when responseId is absent. */
    private UUID freelancerId;
    private UUID clientId;
}
