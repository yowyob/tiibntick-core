package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.MatchingNotificationResponse;
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
import reactor.core.publisher.Mono;

import java.time.Duration;
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

    private static final Duration HEARTBEAT = Duration.ofSeconds(15);

    private final NotificationStreamPort notificationStreamPort;
    private final NotificationStreamAccessGuard accessGuard;

    @GetMapping("/stream/{freelancerId}")
    public Mono<ResponseEntity<Flux<ServerSentEvent<MatchingNotificationResponse>>>> getNotificationStream(
            @PathVariable UUID freelancerId) {
        Flux<ServerSentEvent<MatchingNotificationResponse>> events = notificationStreamPort
                .getNotificationStream(freelancerId)
                .map(MatchingNotificationResponse::from)
                .map(event -> ServerSentEvent.<MatchingNotificationResponse>builder(event)
                        .event("notification")
                        .build());
        // A comment every 15 s: proxies and load balancers close a silent connection after a short idle time,
        // which sent the browser into a reconnect loop.
        Flux<ServerSentEvent<MatchingNotificationResponse>> heartbeat = Flux.interval(HEARTBEAT)
                .map(tick -> ServerSentEvent.<MatchingNotificationResponse>builder().comment("keep-alive").build());
        Flux<ServerSentEvent<MatchingNotificationResponse>> body = Flux.merge(events, heartbeat)
                // Immediate comment keeps the SSE response open under strict Accept negotiation
                // and avoids idle clients seeing a hung connection with no headers flushed.
                .startWith(ServerSentEvent.<MatchingNotificationResponse>builder()
                        .comment("connected")
                        .build());
        return accessGuard.assertOwner(freelancerId).then(Mono.fromSupplier(() -> ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body)));
    }
}
