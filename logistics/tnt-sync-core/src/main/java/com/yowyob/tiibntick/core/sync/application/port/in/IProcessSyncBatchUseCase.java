package com.yowyob.tiibntick.core.sync.application.port.in;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
import reactor.core.publisher.Mono;

public interface IProcessSyncBatchUseCase {
    Mono<SyncPushResponse> processSyncBatch(String userId, String tenantId, String deviceId, SyncPushRequest request);
}
