package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ArchiveDaoZoneUseCase {
    Mono<DaoZone> archive(UUID tenantId, UUID zoneId);
}
