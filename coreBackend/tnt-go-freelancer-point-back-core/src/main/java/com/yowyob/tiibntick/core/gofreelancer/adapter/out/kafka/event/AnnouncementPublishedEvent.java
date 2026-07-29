package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published when an announcement is officially published.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementPublishedEvent {
    private AnnouncementResponseDTO announcement;
    private ClientResponseDTO client;
}
