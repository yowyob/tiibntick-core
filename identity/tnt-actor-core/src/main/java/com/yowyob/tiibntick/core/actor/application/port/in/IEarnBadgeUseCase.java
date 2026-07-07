package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.EarnBadgeCommand;
import reactor.core.publisher.Mono;

public interface IEarnBadgeUseCase {
    Mono<Void> earnBadge(EarnBadgeCommand command);
}
