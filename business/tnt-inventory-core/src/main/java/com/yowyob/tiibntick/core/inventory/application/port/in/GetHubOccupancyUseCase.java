package com.yowyob.tiibntick.core.inventory.application.port.in;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface GetHubOccupancyUseCase {
    Mono<Double> getOccupancyRate(UUID hubId, int maxCapacity);
}
