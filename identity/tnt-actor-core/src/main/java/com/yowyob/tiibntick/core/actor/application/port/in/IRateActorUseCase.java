package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import reactor.core.publisher.Mono;

public interface IRateActorUseCase {
    Mono<Void> rateActor(RateActorCommand command);
}
