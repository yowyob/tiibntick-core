package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.UpdateActorLocationCommand;
import reactor.core.publisher.Mono;

public interface IUpdateActorLocationUseCase {
    Mono<Void> updateLocation(UpdateActorLocationCommand command);
}
