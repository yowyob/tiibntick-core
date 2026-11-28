package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationStreamPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.MatchingNotification;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IStreamUserNotificationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter of {@link NotificationStreamPort}: the live feed is the per-user stream of tnt-realtime-core
 * ({@code broadcastToUser} on the way in, {@code streamNotificationsOf} on the way out), carried by Redis, so a
 * notification reaches the client whichever application instance holds its connection and survives a redeploy of
 * the instance that received the event (the former in-memory sinks did neither).
 *
 * <p>The realtime stream is keyed by the Kernel user id (the subject of the token). The Go stream route and the
 * matching events are keyed by freelancer profile id, so the profile id is translated to its user id on both sides;
 * an id that is not a profile id is taken as a user id already (the access guard also admits the caller's own user id).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationStreamAdapter implements NotificationStreamPort {

    private final IBroadcastNotificationUseCase broadcast;
    private final IStreamUserNotificationsUseCase stream;
    private final GofpFreelancerRepository freelancers;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<MatchingNotification> getNotificationStream(UUID freelancerId) {
        log.info("Client connected to notification stream: {}", freelancerId);
        return streamKey(freelancerId)
                .flatMapMany(userId -> stream.streamNotificationsOf(userId.toString()))
                .mapNotNull(this::parse);
    }

    @Override
    public void pushNotification(MatchingNotification event) {
        streamKey(event.freelancerId())
                .flatMap(userId -> broadcast.broadcastToUser(userId.toString(), event))
                .subscribe(
                        ignored -> { },
                        e -> log.warn("Notification for {} not delivered to the stream: {}", event.freelancerId(), e.toString()));
    }

    private Mono<UUID> streamKey(UUID id) {
        return freelancers.findById(id)
                .mapNotNull(GofpFreelancer::getCoreUserId)
                .defaultIfEmpty(id);
    }

    private MatchingNotification parse(String json) {
        try {
            return objectMapper.readValue(json, MatchingNotification.class);
        } catch (Exception e) {
            log.warn("Unreadable notification dropped from the stream: {}", e.toString());
            return null;
        }
    }
}
