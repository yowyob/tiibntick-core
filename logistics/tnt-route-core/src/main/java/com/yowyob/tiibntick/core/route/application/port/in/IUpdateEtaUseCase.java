package com.yowyob.tiibntick.core.route.application.port.in;

import com.yowyob.tiibntick.core.route.domain.model.EtaResult;
import com.yowyob.tiibntick.core.route.domain.model.GPSMeasurement;
import reactor.core.publisher.Mono;

public interface IUpdateEtaUseCase {
    Mono<EtaResult> updateEta(String missionId, GPSMeasurement measurement);
    Mono<EtaResult> computeInitialEta(String missionId, double totalDistanceKm, double estimatedSpeedKmh);
}
