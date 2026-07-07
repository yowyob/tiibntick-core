package com.yowyob.tiibntick.core.route.application.port.out;

import com.yowyob.tiibntick.core.route.domain.model.Tour;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.util.UUID;

public interface ITourRepository {
    Mono<Tour> save(Tour tour);
    Mono<Tour> findById(UUID id, UUID tenantId);
    Flux<Tour> findByDeliverer(String delivererId, UUID tenantId, LocalDate date);
    Flux<Tour> findByAgencyAndDate(UUID tenantId, UUID agencyId, LocalDate date);
}
