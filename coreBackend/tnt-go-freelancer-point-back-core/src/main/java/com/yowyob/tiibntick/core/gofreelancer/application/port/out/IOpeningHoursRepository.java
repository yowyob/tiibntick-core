package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outgoing port — persistence for OpeningHours (implemented by R2DBC adapter).
 */
public interface IOpeningHoursRepository {

    Mono<OpeningHours> save(OpeningHours openingHours);

    Flux<OpeningHours> findAllByRelayPointId(UUID relayPointId);

    Mono<Void> deleteAllByRelayPointId(UUID relayPointId);
}
