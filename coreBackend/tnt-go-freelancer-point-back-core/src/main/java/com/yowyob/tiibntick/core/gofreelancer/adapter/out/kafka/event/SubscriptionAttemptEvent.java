package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event triggered when a delivery person attempts to subscribe to an announcement.
 *
 * @author François-Charles ATANGA
 * @date 04/02/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionAttemptEvent {
    private UUID announcementId;
    private UUID freelancerId;
    private Instant timestamp;
}
