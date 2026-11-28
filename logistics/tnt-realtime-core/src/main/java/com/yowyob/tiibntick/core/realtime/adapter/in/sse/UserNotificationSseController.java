package com.yowyob.tiibntick.core.realtime.adapter.in.sse;

import com.yowyob.tiibntick.core.realtime.application.port.in.IStreamUserNotificationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Per-user notification stream: {@code GET /api/v1/realtime/sse/notifications}.
 *
 * <p>The stream belongs to the user of the verified token and to nobody else: there is no path or query
 * parameter naming another user. It emits one {@code notification} event per notification broadcast to that
 * user ({@code IBroadcastNotificationUseCase#broadcastToUser}) and a comment frame every 15 seconds so that
 * proxies and load balancers keep the connection open.</p>
 */
@RestController
@RequestMapping("/api/v1/realtime/sse")
public class UserNotificationSseController {

    private static final Logger log = LoggerFactory.getLogger(UserNotificationSseController.class);
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final IStreamUserNotificationsUseCase notifications;

    public UserNotificationSseController(IStreamUserNotificationsUseCase notifications) {
        this.notifications = notifications;
    }

    @GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> notificationsOfCurrentUser(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser
            com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity currentUser) {
        if (currentUser == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }
        String userId = currentUser.userId().toString();
        log.debug("SSE notification stream opened for user {}", userId);

        Flux<ServerSentEvent<String>> data = notifications.streamNotificationsOf(userId)
                .map(json -> ServerSentEvent.<String>builder().event("notification").data(json).build());
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<String>builder().comment("heartbeat").build());

        return Flux.merge(data, heartbeat)
                .startWith(ServerSentEvent.<String>builder().comment("connected").build())
                .doOnCancel(() -> log.debug("SSE notification stream closed for user {}", userId));
    }
}
