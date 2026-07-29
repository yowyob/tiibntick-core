package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event triggered when a delivery person is created.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerCreatedEvent {
    private UUID freelancerId;
    private String email;
}
