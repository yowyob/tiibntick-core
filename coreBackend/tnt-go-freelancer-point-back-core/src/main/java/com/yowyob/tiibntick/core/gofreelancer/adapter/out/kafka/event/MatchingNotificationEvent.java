package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event published when a delivery person is matched with an announcement.
 *
 * @author François-Charles ATANGA
 * @date 19/02/2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingNotificationEvent {
    private UUID freelancerId;
    private UUID announcementId;
    private String title;
    private String message;
}
