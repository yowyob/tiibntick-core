package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.NotificationStreamPort;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Inbound REST adapter exposing real-time notification streams via SSE.
 * Delegates to the NotificationStreamPort outbound port.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreamPort notificationStreamPort;

    @GetMapping(value = "/stream/{freelancerId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MatchingNotificationEvent> getNotificationStream(@PathVariable UUID freelancerId) {
        return notificationStreamPort.getNotificationStream(freelancerId);
    }
}
