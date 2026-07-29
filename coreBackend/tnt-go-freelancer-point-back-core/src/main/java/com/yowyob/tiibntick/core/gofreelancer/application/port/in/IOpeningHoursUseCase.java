package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — OpeningHours management for RelayPoints.
 */
public interface IOpeningHoursUseCase {

    Flux<OpeningHours> getOpeningHoursByRelayPointId(UUID relayPointId);

    Mono<OpeningHours> saveOpeningHours(OpeningHours openingHours);

    Mono<Void> deleteByRelayPointId(UUID relayPointId);
}
