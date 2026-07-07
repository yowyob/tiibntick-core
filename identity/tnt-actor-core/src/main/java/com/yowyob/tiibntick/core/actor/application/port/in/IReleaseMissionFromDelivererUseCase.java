package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.ReleaseMissionCommand;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import reactor.core.publisher.Mono;

public interface IReleaseMissionFromDelivererUseCase {
    Mono<DelivererProfile> releaseMission(ReleaseMissionCommand command);
}
