package com.yowyob.tiibntick.common.kafka;

import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;

/**
 * Makes a Kafka listener wait for the end of its reactive work — decision [G.8] of 2026-11-26
 * ({@code cible-evenements-colis-livraison.md} §3.10 point 2).
 *
 * <p>A {@code @KafkaListener} that hands its work to {@code .subscribe()} returns before the work ends:
 * the container commits the offset, the duplicate filter marks the event processed, and an asynchronous
 * failure is lost. A listener must instead do:
 * <pre>{@code
 * @KafkaListener(topics = TntTopics.DELIVERY_EVENTS, groupId = "...")
 * public void onDeliveryEvent(ConsumerRecord<String, String> record) {
 *     listenerSupport.await(useCase.handle(parse(record)));   // blocks the container thread, bounded
 * }
 * }</pre>
 * Any error — including the timeout — is rethrown to the listener container, whose error handler
 * ({@code TntKafkaErrorHandlingPostProcessor}, yow-event-kernel) retries the record and finally publishes it
 * to {@link TntTopics#DLQ}. Never wrap the call in {@code onErrorResume(e -> Mono.empty())}.
 *
 * <p>Kafka listener containers run on their own threads, so blocking there is legitimate; calling this from
 * a non-blocking Reactor thread (event loop, {@code parallel}) is a programming error and is refused.
 *
 * <p>Reference model: {@code tnt-sync-core EntityChangedEventConsumer}.
 */
public final class ReactiveListenerSupport {

    /** Property holding the maximum duration of a listener's work. */
    public static final String TIMEOUT_PROPERTY = "tnt.kafka.listener.timeout";
    /** Default of {@value #TIMEOUT_PROPERTY} (spec §3.10). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final Duration timeout;

    /** @param timeout maximum duration of a listener's work (strictly positive) */
    public ReactiveListenerSupport(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(TIMEOUT_PROPERTY + " must be > 0, got " + timeout);
        }
        this.timeout = timeout;
    }

    /** @return a support bounded by {@link #DEFAULT_TIMEOUT} */
    public static ReactiveListenerSupport withDefaultTimeout() {
        return new ReactiveListenerSupport(DEFAULT_TIMEOUT);
    }

    public Duration timeout() {
        return timeout;
    }

    /** Waits for {@code work} within the configured timeout; see {@link #awaitBounded(Mono, Duration)}. */
    public <T> T await(final Mono<T> work) {
        return awaitBounded(work, timeout);
    }

    /** Waits for the completion of {@code work} within the configured timeout (elements are discarded). */
    public void await(final Flux<?> work) {
        Objects.requireNonNull(work, "work");
        awaitBounded(work.then(), timeout);
    }

    /**
     * Subscribes to {@code work} and blocks the calling (listener container) thread until it terminates.
     *
     * @param work    the listener's reactive work
     * @param timeout maximum wait; when exceeded the work is cancelled and {@link ListenerTimeoutException} thrown
     * @return the value emitted by {@code work}, {@code null} if it completed empty
     * @throws ListenerTimeoutException if {@code work} did not terminate within {@code timeout}
     * @throws RuntimeException         the error signalled by {@code work} (a checked one is wrapped in
     *                                  {@link ListenerWorkException})
     * @throws IllegalStateException    if called from a non-blocking Reactor thread
     */
    public static <T> T awaitBounded(final Mono<T> work, final Duration timeout) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(timeout, "timeout");
        if (Schedulers.isInNonBlockingThread()) {
            throw new IllegalStateException("ReactiveListenerSupport.awaitBounded called from the non-blocking thread "
                + Thread.currentThread().getName() + ": it is reserved to Kafka listener container threads");
        }
        try {
            return work.timeout(timeout, Mono.error(() -> new ListenerTimeoutException(timeout))).block();
        } catch (RuntimeException e) {
            Throwable cause = Exceptions.unwrap(e);
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ListenerWorkException(cause);
        }
    }

    /** The listener's work did not terminate within its timeout; the record will be retried, then dead-lettered. */
    public static final class ListenerTimeoutException extends RuntimeException {
        public ListenerTimeoutException(final Duration timeout) {
            super("Kafka listener work did not complete within " + timeout);
        }
    }

    /** Wraps a checked exception signalled by the listener's work. */
    public static final class ListenerWorkException extends RuntimeException {
        public ListenerWorkException(final Throwable cause) {
            super("Kafka listener work failed: " + cause, cause);
        }
    }
}
