package com.yowyob.tiibntick.core.sync.application.port.in;

import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import reactor.core.publisher.Mono;

public interface IEnqueueOfflineOpUseCase {
    Mono<Void> enqueue(OfflineOperation operation);
}
