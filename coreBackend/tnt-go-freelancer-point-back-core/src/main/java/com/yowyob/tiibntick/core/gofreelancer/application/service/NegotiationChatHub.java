package com.yowyob.tiibntick.core.gofreelancer.application.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live fan-out of a conversation's events (new message, read receipt, typing) to whoever has it open.
 * Held in memory: it serves the instance the subscribers are connected to. Clients also resynchronise
 * from the stored thread when they (re)connect, so an event missed on another instance is not lost.
 */
@Component
public class NegotiationChatHub {

    /** {@code type} is message | read | typing. Exactly the fields relevant to the type are set. */
    public record ChatEvent(String type, NegotiationChatService.ChatMessage message, UUID actorId,
                            java.time.Instant readAt) {
        public static ChatEvent message(NegotiationChatService.ChatMessage m) {
            return new ChatEvent("message", m, m.senderId(), null);
        }
        public static ChatEvent read(UUID readerId, java.time.Instant at) {
            return new ChatEvent("read", null, readerId, at);
        }
        public static ChatEvent typing(UUID actorId) {
            return new ChatEvent("typing", null, actorId, null);
        }
    }

    private final ConcurrentHashMap<String, Sinks.Many<ChatEvent>> rooms = new ConcurrentHashMap<>();

    static String key(UUID announcementId, UUID threadUserId) {
        return announcementId + ":" + threadUserId;
    }

    public void publish(UUID announcementId, UUID threadUserId, ChatEvent event) {
        Sinks.Many<ChatEvent> sink = rooms.get(key(announcementId, threadUserId));
        if (sink != null) {
            sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(java.time.Duration.ofMillis(50)));
        }
    }

    public Flux<ChatEvent> subscribe(UUID announcementId, UUID threadUserId) {
        String k = key(announcementId, threadUserId);
        return Flux.defer(() -> rooms.computeIfAbsent(k, x -> Sinks.many().multicast().directBestEffort()).asFlux())
                .doFinally(signal -> rooms.computeIfPresent(k, (x, sink) -> sink.currentSubscriberCount() == 0 ? null : sink));
    }

    /** Number of conversations currently open somewhere (observability and tests). */
    public int openRooms() {
        return rooms.size();
    }
}
