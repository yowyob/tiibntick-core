package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event triggered when a delivery person is validated (approved/rejected).
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerValidatedEvent {
    private UUID freelancerId;
    private boolean approved;
}
