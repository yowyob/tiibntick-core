package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Makes a one-off startup job run once across every instance: the first instance that claims the job runs it;
 * a job DONE is never claimed again; a job FAILED, or RUNNING for longer than {@code staleAfter} (crashed
 * instance), can be claimed again.
 */
public interface StartupJobLockPort {

    /** {@code true} when this instance now owns the job and must run it. */
    Mono<Boolean> claim(String job, Duration staleAfter);

    Mono<Void> complete(String job, String details);

    Mono<Void> fail(String job, String details);
}
