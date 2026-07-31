package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationStreamPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Inbound REST adapter exposing real-time matching notification streams via SSE.
 *
 * <p>This is a <strong>local fan-out stream</strong> (Reactor Sinks) for clients
 * already subscribed to matching events consumed from Kafka. It is <em>not</em>
 * a parallel push bus: business notifications (email/push/in-app) must still be
 * sent through {@code tnt-notify-core} via {@code PushNotificationPort} /
 * {@code EmailPort} ({@code GofpNotifyPushAdapter} / {@code GofpNotifyEmailAdapter}).
 *
 * <p>Returns an explicit {@code text/event-stream} body so clients with a
 * loose Accept header (wildcard or shared JSON harness defaults) are not
 * rejected with HTTP 406.
 *
 * @author MANFOUO BRAUN
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreamPort notificationStreamPort;

    @GetMapping("/stream/{freelancerId}")
    public ResponseEntity<Flux<ServerSentEvent<MatchingNotificationEvent>>> getNotificationStream(
            @PathVariable UUID freelancerId) {
        Flux<ServerSentEvent<MatchingNotificationEvent>> body = notificationStreamPort
                .getNotificationStream(freelancerId)
                .map(event -> ServerSentEvent.<MatchingNotificationEvent>builder(event)
                        .event("notification")
                        .build())
                // Immediate comment keeps the SSE response open under strict Accept negotiation
                // and avoids idle clients seeing a hung connection with no headers flushed.
                .startWith(ServerSentEvent.<MatchingNotificationEvent>builder()
                        .comment("connected")
                        .build());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }
}
