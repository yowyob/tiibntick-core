package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IOpeningHoursRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges IOpeningHoursRepository to the R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class OpeningHoursRepositoryAdapter implements IOpeningHoursRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.OpeningHoursRepository r2dbcRepository;

    @Override
    public Mono<OpeningHours> save(OpeningHours openingHours) {
        return r2dbcRepository.save(openingHours);
    }

    @Override
    public Flux<OpeningHours> findAllByRelayPointId(UUID relayPointId) {
        return r2dbcRepository.findAllByRelayPointId(relayPointId);
    }

    @Override
    public Mono<Void> deleteAllByRelayPointId(UUID relayPointId) {
        return r2dbcRepository.deleteAllByRelayPointId(relayPointId);
    }
}
