package com.yowyob.tiibntick.core.route.application.port.out;

import com.yowyob.tiibntick.core.route.domain.model.KalmanState;
import reactor.core.publisher.Mono;

public interface IKalmanStateRepository {
    Mono<KalmanState> save(KalmanState state);
    Mono<KalmanState> findByMissionId(String missionId);
    Mono<Void> deleteByMissionId(String missionId);
}
