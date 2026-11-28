package com.yowyob.tiibntick.common.kafka;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactiveListenerSupportTest {

    private final ReactiveListenerSupport support = new ReactiveListenerSupport(Duration.ofMillis(500));

    @Test
    void waitsForTheEndOfTheWorkAndReturnsItsValue() {
        AtomicBoolean done = new AtomicBoolean();
        String value = support.await(Mono.delay(Duration.ofMillis(100)).doOnNext(t -> done.set(true)).thenReturn("ok"));
        assertThat(value).isEqualTo("ok");
        assertThat(done).isTrue();
    }

    @Test
    void emptyWorkReturnsNull() {
        String value = support.await(Mono.<String>empty());
        assertThat(value).isNull();
    }

    @Test
    void waitsForAFluxToComplete() {
        AtomicInteger seen = new AtomicInteger();
        support.await(Flux.range(1, 5).delayElements(Duration.ofMillis(10)).doOnNext(i -> seen.incrementAndGet()));
        assertThat(seen).hasValue(5);
    }

    @Test
    void anErrorOfTheWorkReachesTheListenerContainer() {
        IllegalStateException failure = new IllegalStateException("kernel down");
        assertThatThrownBy(() -> support.await(Mono.delay(Duration.ofMillis(20)).then(Mono.error(failure))))
            .isSameAs(failure);
    }

    @Test
    void aCheckedErrorIsWrapped() {
        IOException failure = new IOException("io");
        assertThatThrownBy(() -> support.await(Mono.error(failure)))
            .isInstanceOf(ReactiveListenerSupport.ListenerWorkException.class)
            .hasCause(failure);
    }

    @Test
    void aWorkThatNeverEndsTimesOutAndIsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        long start = System.nanoTime();
        assertThatThrownBy(() -> support.await(Mono.never().doOnCancel(() -> cancelled.set(true))))
            .isInstanceOf(ReactiveListenerSupport.ListenerTimeoutException.class)
            .hasMessageContaining("PT0.5S");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(cancelled).isTrue();
    }

    @Test
    void refusesToBlockANonBlockingThread() {
        Throwable error = Mono.fromCallable(() -> support.await(Mono.just(1)))
            .subscribeOn(Schedulers.parallel())
            .map(Object::toString)
            .onErrorResume(e -> Mono.just(e.getClass().getName() + ":" + e.getMessage()))
            .map(RuntimeException::new)
            .block(Duration.ofSeconds(5));
        assertThat(error.getMessage()).startsWith(IllegalStateException.class.getName())
            .contains("non-blocking thread");
    }

    @Test
    void timeoutMustBePositive() {
        assertThatThrownBy(() -> new ReactiveListenerSupport(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ReactiveListenerSupport.withDefaultTimeout().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(ReactiveListenerSupport.TIMEOUT_PROPERTY).isEqualTo("tnt.kafka.listener.timeout");
    }
}
