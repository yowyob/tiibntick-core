package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service for broadcasting generic notifications via WebSocket.
 * Used by tnt-notify-core to deliver in-app real-time notifications to
 * connected clients without going through the full notification pipeline.
 *
 * @author MANFOUO Braun
 */
@Service
public class NotificationBroadcastApplicationService implements IBroadcastNotificationUseCase {

    private final IWebSocketBroadcaster broadcaster;

    public NotificationBroadcastApplicationService(IWebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public Mono<Void> broadcastToTopic(String topic, Object payload) {
        return broadcaster.broadcast(new BroadcastTopic(topic), payload);
    }

    @Override
    public Mono<Void> broadcastToUser(String userId, Object payload) {
        return broadcaster.broadcast(BroadcastTopic.forUser(userId), payload);
    }
}
