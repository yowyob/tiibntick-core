package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Event published when a delivery person updates their location.
 *
 * @author François-Charles ATANGA
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerLocationUpdatedEvent {
    private UUID freelancerId;
    private Float latitude;
    private Float longitude;
}
