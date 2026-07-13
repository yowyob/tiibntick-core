package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.application.port.in.command.CreateDaoZoneCommand;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;
import reactor.core.publisher.Mono;

public interface CreateDaoZoneUseCase {
    Mono<DaoZone> create(CreateDaoZoneCommand command);
}
