package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.MissionRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link RecordMissionUseCase}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpRecordMissionUseCase implements RecordMissionUseCase {

    @Override
    public Mono<String> recordCreated(final String missionId, final String actorId,
                                      final String tenantId, final int packageCount) {
        return Mono.empty();
    }

    @Override
    public Mono<String> recordCompleted(final String missionId, final String actorId,
                                        final String tenantId) {
        return Mono.empty();
    }

    @Override
    public Mono<String> recordCancelled(final String missionId, final String tenantId,
                                        final String cancelReason) {
        return Mono.empty();
    }

    @Override
    public Flux<MissionRecord> getMissionHistory(final String missionId, final String tenantId) {
        return Flux.empty();
    }
}
