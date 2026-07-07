package com.yowyob.tiibntick.core.sync.application.port.in;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPullResponse;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface IComputeDeltaUseCase {
    Mono<SyncPullResponse> computeDelta(String userId, String tenantId, String deviceId,
                                        String syncToken, Set<String> filterAggregates);
}
