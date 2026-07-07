package com.yowyob.tiibntick.core.accounting.application.port.out;

import reactor.core.publisher.Mono;
import java.util.UUID;

/** Generates unique monotonic journal sequence numbers (per tenant per year). Author: MANFOUO Braun */
public interface JournalSequencePort {
    Mono<Long> nextSequence(UUID tenantId, int year);
}
