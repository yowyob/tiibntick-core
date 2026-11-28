package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Mono;

/**
 * <b>Outbound port</b> — cheap reachability probe of the message broker.
 *
 * <p>Used by the outbox relay before a cycle: while the broker is unreachable, a publish blocks
 * for the producer's {@code max.block.ms} and every attempt would be burnt for nothing (pushing
 * healthy envelopes towards DEAD). The relay therefore skips the cycle instead — a broker outage
 * delays envelopes, it never kills them.
 */
public interface BrokerAvailabilityPort {

    /**
     * @return {@code true} when the broker answered within a short timeout; never errors
     */
    Mono<Boolean> isAvailable();
}
