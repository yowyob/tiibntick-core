package com.yowyob.tiibntick.common.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthenticatedSessionPublisher — fire-and-forget fan-out")
class AuthenticatedSessionPublisherTest {

    private static final class Recording implements AuthenticatedSessionListener {
        final List<String> tokens = new ArrayList<>();
        final AtomicReference<String> profileCtx = new AtomicReference<>();
        int profiles;
        @Override public Mono<Void> onTokenIssued(String t) { tokens.add(t); return Mono.empty(); }
        @Override public Mono<Void> onProfileCreated() {
            return Mono.deferContextual(ctx -> {
                profiles++;
                profileCtx.set(ctx.getOrDefault("marker", "none"));
                return Mono.empty();
            });
        }
    }

    private static final class Failing implements AuthenticatedSessionListener {
        @Override public Mono<Void> onTokenIssued(String t) { return Mono.error(new IllegalStateException("kernel down")); }
        @Override public Mono<Void> onProfileCreated() { throw new IllegalStateException("boom at assembly"); }
    }

    @Test
    @DisplayName("a failing listener never stops the others nor the caller")
    void failureIsIsolated() {
        Recording ok = new Recording();
        var publisher = new AuthenticatedSessionPublisher(() -> List.of(new Failing(), ok));

        publisher.tokenIssued("tok-1");
        StepVerifier.create(publisher.profileCreated()).verifyComplete();

        assertThat(ok.tokens).containsExactly("tok-1");
        assertThat(ok.profiles).isEqualTo(1);
    }

    @Test
    @DisplayName("profileCreated hands the caller's Reactor context to the listeners")
    void profileCreatedKeepsContext() {
        Recording ok = new Recording();
        var publisher = new AuthenticatedSessionPublisher(() -> List.of(ok));

        StepVerifier.create(publisher.profileCreated().contextWrite(c -> c.put("marker", "caller-ctx")))
                .verifyComplete();

        assertThat(ok.profileCtx.get()).isEqualTo("caller-ctx");
    }

    @Test
    @DisplayName("blank / null tokens are ignored")
    void blankTokenIgnored() {
        Recording ok = new Recording();
        var publisher = new AuthenticatedSessionPublisher(() -> List.of(ok));
        publisher.tokenIssued(null);
        publisher.tokenIssued("  ");
        assertThat(ok.tokens).isEmpty();
    }
}
