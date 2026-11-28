package com.yowyob.tiibntick.core.realtime.adapter.in.sse;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.application.port.in.IStreamUserNotificationsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("The per-user notification stream belongs to the user of the token")
class UserNotificationSseControllerTest {

    private final IStreamUserNotificationsUseCase notifications = mock(IStreamUserNotificationsUseCase.class);
    private final UserNotificationSseController controller = new UserNotificationSseController(notifications);
    private final UUID userId = UUID.randomUUID();

    private TntUserIdentity identity() {
        return new TntUserIdentity(userId, UUID.randomUUID(), UUID.randomUUID(), null, null, Set.of(), false);
    }

    @Test
    @DisplayName("an anonymous caller is refused with 401 and nothing is subscribed")
    void anonymousIsRefused() {
        StepVerifier.create(controller.notificationsOfCurrentUser(null))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }).verify();
        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("the stream opens with a comment, then carries each notification of the token's user as a 'notification' event")
    void streamsTheNotificationsOfTheTokenUser() {
        Sinks.Many<String> source = Sinks.many().multicast().directBestEffort();
        when(notifications.streamNotificationsOf(anyString())).thenReturn(source.asFlux());

        StepVerifier.create(controller.notificationsOfCurrentUser(identity()).take(2))
                .assertNext(first -> assertThat(first.comment()).isEqualTo("connected"))
                .then(() -> source.tryEmitNext("{\"title\":\"hello\"}"))
                .assertNext(ev -> {
                    assertThat(ev.event()).isEqualTo("notification");
                    assertThat(ev.data()).isEqualTo("{\"title\":\"hello\"}");
                })
                .verifyComplete();
        verify(notifications).streamNotificationsOf(userId.toString());
    }

    @Test
    @DisplayName("a keep-alive comment is sent every 15 seconds on an idle stream")
    void heartbeat() {
        when(notifications.streamNotificationsOf(anyString())).thenReturn(Flux.never());

        StepVerifier.withVirtualTime(() -> controller.notificationsOfCurrentUser(identity()))
                .assertNext(first -> assertThat(first.comment()).isEqualTo("connected"))
                .expectNoEvent(Duration.ofSeconds(14))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(beat -> assertThat(beat.comment()).isEqualTo("heartbeat"))
                .thenCancel()
                .verify();
    }
}
