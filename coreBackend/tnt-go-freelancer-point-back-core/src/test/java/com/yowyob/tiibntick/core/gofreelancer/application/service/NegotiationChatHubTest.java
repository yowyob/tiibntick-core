package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatHub.ChatEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Chat hub: live fan-out per conversation")
class NegotiationChatHubTest {

    private final NegotiationChatHub hub = new NegotiationChatHub();
    private final UUID announcement = UUID.randomUUID();
    private final UUID thread = UUID.randomUUID();

    @Test
    @DisplayName("every subscriber of a conversation receives each event, in order")
    void fanOut() {
        List<String> one = new ArrayList<>();
        List<String> two = new ArrayList<>();
        hub.subscribe(announcement, thread).subscribe(e -> one.add(e.type()));
        hub.subscribe(announcement, thread).subscribe(e -> two.add(e.type()));
        hub.publish(announcement, thread, ChatEvent.typing(UUID.randomUUID()));
        hub.publish(announcement, thread, ChatEvent.read(UUID.randomUUID(), java.time.Instant.now()));
        assertThat(one).containsExactly("typing", "read");
        assertThat(two).containsExactly("typing", "read");
    }

    @Test
    @DisplayName("another conversation, even of the same announcement, hears nothing")
    void isolation() {
        List<String> other = new ArrayList<>();
        hub.subscribe(announcement, UUID.randomUUID()).subscribe(e -> other.add(e.type()));
        hub.subscribe(UUID.randomUUID(), thread).subscribe(e -> other.add(e.type()));
        hub.publish(announcement, thread, ChatEvent.typing(UUID.randomUUID()));
        assertThat(other).isEmpty();
    }

    @Test
    @DisplayName("publishing to a conversation nobody has open is a no-op")
    void nobodyListening() {
        hub.publish(announcement, thread, ChatEvent.typing(UUID.randomUUID()));
        assertThat(hub.openRooms()).isZero();
    }

    @Test
    @DisplayName("a room is released when its last subscriber leaves, and a late subscriber still works")
    void roomsAreReleased() {
        Disposable a = hub.subscribe(announcement, thread).subscribe();
        Disposable b = hub.subscribe(announcement, thread).subscribe();
        assertThat(hub.openRooms()).isEqualTo(1);
        a.dispose();
        assertThat(hub.openRooms()).isEqualTo(1);
        b.dispose();
        assertThat(hub.openRooms()).isZero();

        List<String> late = new ArrayList<>();
        hub.subscribe(announcement, thread).subscribe(e -> late.add(e.type()));
        hub.publish(announcement, thread, ChatEvent.typing(UUID.randomUUID()));
        assertThat(late).containsExactly("typing");
    }
}
