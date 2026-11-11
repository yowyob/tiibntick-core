package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.MatchingNotification;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStreamAdapterTest {

    private final NotificationStreamAdapter adapter = new NotificationStreamAdapter();
    private final UUID freelancer = UUID.randomUUID();

    private MatchingNotification event(String title) {
        return new MatchingNotification(freelancer, UUID.randomUUID(), title, "m");
    }

    @Test
    void aClientReceivesWhatIsPushedWhileConnected() {
        StepVerifier.create(adapter.getNotificationStream(freelancer).take(1))
                .then(() -> adapter.pushNotification(event("a")))
                .assertNext(n -> assertThat(n.title()).isEqualTo("a"))
                .verifyComplete();
    }

    @Test
    void aReconnectionAfterTheLastClientLeftStaysOpenAndReceivesNotifications() {
        // first client connects and leaves: the sink used to cancel itself here
        Disposable first = adapter.getNotificationStream(freelancer).subscribe();
        first.dispose();

        CopyOnWriteArrayList<String> got = new CopyOnWriteArrayList<>();
        boolean[] completed = {false};
        Disposable second = adapter.getNotificationStream(freelancer)
                .subscribe(n -> got.add(n.title()), e -> { }, () -> completed[0] = true);

        adapter.pushNotification(event("after-reconnect"));

        assertThat(completed[0]).as("the stream must not complete on reconnection").isFalse();
        assertThat(got).containsExactly("after-reconnect");
        second.dispose();
    }

    @Test
    void severalClientsOfTheSameFreelancerAllReceiveAndOneLeavingDoesNotAffectTheOther() {
        CopyOnWriteArrayList<String> a = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> b = new CopyOnWriteArrayList<>();
        Disposable da = adapter.getNotificationStream(freelancer).subscribe(n -> a.add(n.title()));
        Disposable db = adapter.getNotificationStream(freelancer).subscribe(n -> b.add(n.title()));
        adapter.pushNotification(event("x"));
        da.dispose();
        adapter.pushNotification(event("y"));
        assertThat(a).containsExactly("x");
        assertThat(b).containsExactly("x", "y");
        db.dispose();
    }

    @Test
    void pushingWithoutAnyClientIsHarmless() {
        adapter.pushNotification(event("nobody"));
        adapter.getNotificationStream(freelancer).subscribe().dispose();
        adapter.pushNotification(event("nobody-either"));
        StepVerifier.create(adapter.getNotificationStream(freelancer).take(Duration.ofMillis(100))).verifyComplete();
    }
}
