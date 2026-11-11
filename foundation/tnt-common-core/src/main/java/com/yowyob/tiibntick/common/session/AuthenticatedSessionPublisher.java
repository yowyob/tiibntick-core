package com.yowyob.tiibntick.common.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

/**
 * Fans session events out to every {@link AuthenticatedSessionListener}, fire-and-forget:
 * the caller (login proxy, profile controller) is never delayed nor failed by a listener.
 *
 * <p>Listeners are resolved lazily on each event ({@code listenersSupplier}) so this
 * foundation bean never forces early initialisation of the higher-layer beans that
 * implement them.
 *
 * @author MANFOUO Braun
 */
public class AuthenticatedSessionPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedSessionPublisher.class);

    private final Supplier<List<AuthenticatedSessionListener>> listenersSupplier;

    public AuthenticatedSessionPublisher(Supplier<List<AuthenticatedSessionListener>> listenersSupplier) {
        this.listenersSupplier = listenersSupplier;
    }

    /** A fresh access token was issued; notify listeners in the background. */
    public void tokenIssued(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        for (AuthenticatedSessionListener listener : listenersSupplier.get()) {
            safely(listener, "onTokenIssued", () -> listener.onTokenIssued(accessToken));
        }
    }

    /**
     * The caller's profile was just created. Returns an empty {@code Mono} immediately;
     * listeners run in the background with the caller's Reactor context (JWT relay).
     */
    public Mono<Void> profileCreated() {
        return Mono.deferContextual(ctx -> {
            for (AuthenticatedSessionListener listener : listenersSupplier.get()) {
                safely(listener, "onProfileCreated", () -> listener.onProfileCreated().contextWrite(ctx));
            }
            return Mono.empty();
        });
    }

    private static void safely(AuthenticatedSessionListener listener, String event, Supplier<Mono<Void>> work) {
        try {
            work.get().subscribe(
                    unused -> { },
                    ex -> log.warn("Session listener {} failed on {} (ignored): {}",
                            listener.getClass().getSimpleName(), event, ex.toString()));
        } catch (RuntimeException ex) {
            log.warn("Session listener {} threw on {} (ignored): {}",
                    listener.getClass().getSimpleName(), event, ex.toString());
        }
    }
}
