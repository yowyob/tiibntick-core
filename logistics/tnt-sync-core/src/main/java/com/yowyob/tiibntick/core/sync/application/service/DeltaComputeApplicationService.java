package com.yowyob.tiibntick.core.sync.application.service;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPullResponse;
import com.yowyob.tiibntick.core.sync.application.port.in.IComputeDeltaUseCase;
import com.yowyob.tiibntick.core.sync.domain.model.SyncDelta;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.service.DeltaSyncDomainService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
public class DeltaComputeApplicationService implements IComputeDeltaUseCase {

    //private static final Logger log = LoggerFactory.getLogger(DeltaComputeApplicationService.class);

    private final DeltaSyncDomainService deltaSyncService;
    private final Counter pullCounter;

    public DeltaComputeApplicationService(DeltaSyncDomainService deltaSyncService, MeterRegistry meterRegistry) {
        this.deltaSyncService = deltaSyncService;
        this.pullCounter = Counter.builder("tnt.sync.pull.total")
                .description("Total pull-sync requests processed")
                .register(meterRegistry);
    }

    @Override
    public Mono<SyncPullResponse> computeDelta(String userId, String tenantId, String deviceId,
                                                String syncToken, Set<String> filterAggregates) {
        pullCounter.increment();

        return Mono.fromCallable(() -> resolveToken(syncToken, userId, tenantId, deviceId))
                .flatMap(token -> {
                    boolean isBootstrap = token.isInitial();
                    Mono<SyncDelta> deltaMono = isBootstrap
                            ? deltaSyncService.computeBootstrapDelta(tenantId, userId, deviceId, filterAggregates)
                            : deltaSyncService.computeDelta(token, tenantId, filterAggregates);
                    return deltaMono.map(this::toResponse);
                });
    }

    private SyncPullResponse toResponse(SyncDelta delta) {
        var records = delta.records().stream()
                .map(r -> new SyncPullResponse.DeltaRecordDto(
                        r.aggregateType(), r.aggregateId(),
                        r.operation().name(), r.payload(),
                        r.serverVersion(), r.serverTimestamp()))
                .toList();

        return new SyncPullResponse(
                delta.newToken().value(),
                records.size(),
                records,
                delta.generatedAt(),
                false,
                records.isEmpty() ? 300 : 60
        );
    }

    private SyncToken resolveToken(String tokenValue, String userId, String tenantId, String deviceId) {
        return SyncToken.parse(tokenValue, userId, tenantId, deviceId);
    }
}
